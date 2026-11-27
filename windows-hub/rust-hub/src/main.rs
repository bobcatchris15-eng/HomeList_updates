mod store;
#[cfg(test)]
mod tests;
mod updates;
mod wall;

use anyhow::{Context, Result, bail, ensure};
use iroh::{
    Endpoint, EndpointId,
    endpoint::{Connection, RecvStream, SendStream, presets},
};
use serde::{Deserialize, Serialize, de::DeserializeOwned};
use std::{
    collections::BTreeMap,
    env, fs,
    io::{self, IsTerminal, Write},
    path::{Path, PathBuf},
    sync::Arc,
    time::Duration,
};
use store::{Config, Household, Snapshot, Tables};
use tokio::{
    sync::{Mutex, Semaphore},
    time::timeout,
};
use url::Url;

const ALPN: &[u8] = b"homelist/gossip/2";
const MAX_FRAME_BYTES: usize = 16 * 1024 * 1024;
const EXCHANGE_TIMEOUT: Duration = Duration::from_secs(15);

#[derive(Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
struct Request {
    protocol_version: u32,
    household_id: String,
    pairing_code: String,
    payload: Option<Snapshot>,
}

#[derive(Deserialize, Serialize)]
struct Response {
    ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    error: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    payload: Option<Snapshot>,
}

impl Response {
    fn error(name: &str) -> Self {
        Self {
            ok: false,
            error: Some(name.into()),
            payload: None,
        }
    }
}

pub(crate) struct Hub {
    dir: PathBuf,
    pub(crate) config: Config,
    pub(crate) stores: BTreeMap<String, Mutex<Tables>>,
    endpoint_id: String,
    version: String,
}

impl Hub {
    fn open(dir: PathBuf, config: Config, endpoint_id: String, version: String) -> Result<Self> {
        let mut stores = BTreeMap::new();
        for household in &config.households {
            let id = &household.household_id;
            let path = store::store_path(&dir, id);
            let loaded = store::load_json::<Tables>(&path)?.unwrap_or_default();
            let mut scoped = Tables::default();
            scoped.merge(loaded, id, &config.node_id, "", "");
            if household.is_founder && scoped.places.is_empty() {
                let home = serde_json::json!({
                    "id": uuid::Uuid::new_v4().to_string(), "householdId": id,
                    "name": "Home", "latitude": 0.0, "longitude": 0.0, "radiusMeters": 200,
                    "isHomePlace": true, "isDeleted": false, "updatedAt": store::now_ms()
                });
                scoped.places.push(serde_json::from_value(home)?);
                store::save_json(&path, &scoped)?;
            }
            stores.insert(id.clone(), Mutex::new(scoped));
        }
        Ok(Self {
            dir,
            config,
            stores,
            endpoint_id,
            version,
        })
    }

    async fn process(&self, request: Request, remote: &str) -> Result<Response> {
        let id = &request.household_id;
        if request.protocol_version != 2
            || !store::safe_household_id(id)
            || request.payload.as_ref().is_some_and(|payload| {
                payload.protocol_version != 2
                    || payload.household_id != *id
                    || payload.sender_endpoint_id != remote
            })
        {
            return Ok(Response::error("bad_request"));
        }
        let Some(house) = self
            .config
            .households
            .iter()
            .find(|house| house.household_id == *id)
        else {
            return Ok(Response::error("unknown_household"));
        };
        if !store::pairing_code_matches(&house.pairing_code, &request.pairing_code) {
            return Ok(Response::error("invalid_pairing_code"));
        }
        let mut tables = self.stores[id].lock().await;
        if let Some(incoming) = request.payload {
            let mut next = tables.clone();
            next.merge(
                incoming.tables,
                id,
                &self.config.node_id,
                &incoming.sender_node_id,
                remote,
            );
            // Keep the guard through the write. Cancellation must not let a second writer race it.
            store::save_json(&store::store_path(&self.dir, id), &next)?;
            *tables = next;
        }
        let snapshot = Snapshot::from_store(
            house,
            &self.config,
            &self.endpoint_id,
            &self.version,
            &tables,
        );
        Ok(Response {
            ok: true,
            error: None,
            payload: Some(snapshot),
        })
    }
}

async fn read_frame<T: DeserializeOwned>(recv: &mut RecvStream) -> Result<T> {
    let mut header = [0u8; 4];
    recv.read_exact(&mut header).await?;
    let size = u32::from_be_bytes(header) as usize;
    ensure!(
        (1..=MAX_FRAME_BYTES).contains(&size),
        "Invalid HomeList frame length"
    );
    let mut bytes = vec![0; size];
    recv.read_exact(&mut bytes).await?;
    Ok(serde_json::from_slice(&bytes)?)
}

async fn write_frame(send: &mut SendStream, value: &impl Serialize) -> Result<()> {
    let json = serde_json::to_vec(value)?;
    ensure!(
        (1..=MAX_FRAME_BYTES).contains(&json.len()),
        "HomeList snapshot exceeds the frame limit"
    );
    send.write_all(&(json.len() as u32).to_be_bytes()).await?;
    send.write_all(&json).await?;
    send.finish()?;
    Ok(())
}

async fn serve_connection(hub: Arc<Hub>, connection: Connection) -> Result<()> {
    let (mut send, mut recv) = connection.accept_bi().await?;
    let response = match read_frame::<Request>(&mut recv).await {
        Ok(request) => match hub
            .process(request, &connection.remote_id().to_string())
            .await
        {
            Ok(response) => response,
            Err(error) => {
                eprintln!("Could not save household sync: {error:#}");
                Response::error("storage_error")
            }
        },
        Err(_) => Response::error("bad_request"),
    };
    write_frame(&mut send, &response).await?;
    send.stopped().await?;
    Ok(())
}

async fn serve(endpoint: &Endpoint, hub: Arc<Hub>) {
    let slots = Arc::new(Semaphore::new(8));
    loop {
        let Ok(permit) = slots.clone().acquire_owned().await else {
            return;
        };
        let Some(incoming) = endpoint.accept().await else {
            return;
        };
        let hub = hub.clone();
        tokio::spawn(async move {
            let _permit = permit;
            let _ = timeout(EXCHANGE_TIMEOUT, async {
                serve_connection(hub, incoming.await?).await
            })
            .await;
        });
    }
}

async fn bind(key: iroh::SecretKey) -> Result<Endpoint> {
    Ok(Endpoint::builder(presets::N0)
        .secret_key(key)
        .alpns(vec![ALPN.to_vec()])
        .bind()
        .await?)
}

fn invite_url(endpoint: &str, house: &Household) -> String {
    let mut url = Url::parse("homelist://join").expect("static invite prefix");
    url.query_pairs_mut()
        .append_pair("ep", endpoint)
        .append_pair("hid", &house.household_id)
        .append_pair("code", &house.pairing_code)
        .append_pair("name", &house.household_name);
    // url::query_pairs_mut uses form encoding. Use percent escapes for Android URIs.
    url.as_str().replace('+', "%20")
}

fn print_invites(endpoint: &str, config: &Config) -> Result<()> {
    println!("Endpoint ID: {endpoint}");
    for house in &config.households {
        let link = invite_url(endpoint, house);
        println!("\n{}\n{link}", house.household_name);
        if io::stdout().is_terminal() {
            if let Err(error) = qr2term::print_qr(link) {
                eprintln!("Could not print the QR code: {error}");
            }
        }
    }
    Ok(())
}

async fn add_existing(
    endpoint: &Endpoint,
    dir: &Path,
    config: &mut Config,
    raw: &str,
    version: &str,
) -> Result<()> {
    let uri = Url::parse(raw)?;
    ensure!(
        uri.scheme() == "homelist" && uri.host_str() == Some("join") && uri.fragment().is_none(),
        "Invalid HomeList invite link"
    );
    let mut fields = BTreeMap::new();
    for (key, value) in uri.query_pairs() {
        ensure!(
            fields
                .insert(key.into_owned(), value.into_owned())
                .is_none(),
            "Duplicate invite field"
        );
    }
    let peer: EndpointId = fields
        .remove("ep")
        .context("Invite is missing its endpoint")?
        .parse()?;
    let id = fields
        .remove("hid")
        .context("Invite is missing its household ID")?;
    let code = fields
        .remove("code")
        .context("Invite is missing its pairing code")?
        .trim()
        .to_ascii_uppercase();
    ensure!(store::safe_household_id(&id), "Invalid household ID");
    ensure!(
        !config
            .households
            .iter()
            .any(|house| house.household_id.eq_ignore_ascii_case(&id)),
        "This hub already hosts the household"
    );
    let response = exchange(
        endpoint,
        peer,
        &Request {
            protocol_version: 2,
            household_id: id.clone(),
            pairing_code: code.clone(),
            payload: None,
        },
    )
    .await?;
    let incoming = response.payload.filter(|_| response.ok).with_context(|| {
        format!(
            "Join rejected ({})",
            response.error.as_deref().unwrap_or("bad response")
        )
    })?;
    let mut tables = Tables::default();
    tables.merge(
        incoming.tables,
        &id,
        &config.node_id,
        &incoming.sender_node_id,
        &peer.to_string(),
    );
    let house = Household {
        household_id: id.clone(),
        household_name: incoming.household_name,
        pairing_code: code.clone(),
        display_name: "Home Hub".into(),
        is_founder: false,
    };
    store::save_json(&store::store_path(dir, &id), &tables)?;
    let own = Snapshot::from_store(&house, config, &endpoint.id().to_string(), version, &tables);
    config.households.push(house);
    store::save_config(dir, config)?;
    let registration = exchange(
        endpoint,
        peer,
        &Request {
            protocol_version: 2,
            household_id: id,
            pairing_code: code,
            payload: Some(own),
        },
    )
    .await;
    if !registration.is_ok_and(|result| result.ok) {
        eprintln!(
            "Household saved. In HomeList, save this hub's Endpoint ID in Household Settings to start syncing."
        );
    }
    Ok(())
}

async fn exchange(endpoint: &Endpoint, peer: EndpointId, request: &Request) -> Result<Response> {
    timeout(EXCHANGE_TIMEOUT, async {
        let connection = endpoint.connect(peer, ALPN).await?;
        let (mut send, mut recv) = connection.open_bi().await?;
        write_frame(&mut send, request).await?;
        let response: Response = read_frame(&mut recv).await?;
        if response.ok {
            ensure!(
                response
                    .payload
                    .as_ref()
                    .is_some_and(|p| p.household_id == request.household_id
                        && p.protocol_version == 2
                        && p.sender_endpoint_id == connection.remote_id().to_string()),
                "Peer sent an invalid household snapshot"
            );
        }
        Ok(response)
    })
    .await
    .context("HomeList sync timed out")?
}

fn hub_dir() -> Result<PathBuf> {
    Ok(env::current_exe()?
        .parent()
        .context("Finding the hub folder")?
        .to_path_buf())
}

fn prompt_household() -> Result<String> {
    print!("Household name [Our Family]: ");
    io::stdout().flush()?;
    let mut name = String::new();
    io::stdin().read_line(&mut name)?;
    let name = name.trim();
    Ok(if name.is_empty() {
        "Our Family".into()
    } else {
        name.into()
    })
}

#[tokio::main]
async fn main() -> Result<()> {
    let args = env::args().skip(1).collect::<Vec<_>>();
    if args
        .first()
        .is_some_and(|arg| arg == "--help" || arg == "-h")
    {
        println!("HomeListHub [--add NAME | --join INVITE | --invites]");
        return Ok(());
    }
    let dir = hub_dir()?;
    let lock = fs::OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .open(dir.join("homelist-hub.lock"))?;
    lock.try_lock()
        .context("Stop the other HomeList Hub process in this folder first")?;

    let mut config = store::load_or_migrate_config(&dir)?;
    let identity = store::load_identity(&dir)?;
    let id = identity.public().to_string();
    let version = fs::read_to_string(dir.join("VERSION.txt"))
        .unwrap_or_else(|_| env!("CARGO_PKG_VERSION").into());
    let version = version.trim();
    match args.as_slice() {
        [command] if command == "--invites" => return print_invites(&id, &config),
        [command, name] if command == "--add" => {
            ensure!(!name.trim().is_empty(), "Enter a household name");
            config.households.push(Household::new(name.trim().into()));
            store::save_config(&dir, &config)?;
            return print_invites(&id, &config);
        }
        [command, invite] if command == "--join" => {
            let endpoint = bind(identity).await?;
            let result = add_existing(&endpoint, &dir, &mut config, invite, version).await;
            endpoint.close().await;
            result?;
            return print_invites(&id, &config);
        }
        [] => {}
        _ => bail!("Use HomeListHub --help for commands"),
    }
    if config.households.is_empty() {
        config.households.push(Household::new(prompt_household()?));
        store::save_config(&dir, &config)?;
    }
    let endpoint = bind(identity).await?;
    let listener = tokio::net::TcpListener::bind(("127.0.0.1", config.wall_board_port))
        .await
        .context("Binding the local wall board")?;
    let port = listener.local_addr()?.port();
    print_invites(&id, &config)?;
    println!("\nWall Board: http://localhost:{port}");
    let hub = Arc::new(Hub::open(dir, config, id, version.into())?);
    let updates = tokio::spawn(updates::check_daily(
        hub.config.update_release_url.clone(),
        hub.version.clone(),
    ));
    tokio::select! {
        _ = serve(&endpoint, hub.clone()) => {},
        result = axum::serve(listener, wall::router(hub, port)) => result?,
        result = tokio::signal::ctrl_c() => result?,
    }
    updates.abort();
    endpoint.close().await;
    Ok(())
}
