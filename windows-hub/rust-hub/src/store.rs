use anyhow::{Context, Result, ensure};
use iroh::SecretKey;
use serde::{Deserialize, Serialize, de::DeserializeOwned};
use serde_json::{Map, Value, json};
use std::{
    collections::BTreeMap,
    fs, io,
    io::Write,
    path::{Path, PathBuf},
    time::{SystemTime, UNIX_EPOCH},
};
use subtle::ConstantTimeEq;
use uuid::Uuid;

pub const DEFAULT_RELEASE_URL: &str =
    "https://github.com/bobcatchris15-eng/HomeList_updates/releases/latest";
const CONFIG_FILE: &str = "homelist-hub-config.json";
const LEGACY_DATA_FILE: &str = "homelist-hub-data.json";
pub type Row = Map<String, Value>;

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Household {
    pub household_id: String,
    pub household_name: String,
    pub pairing_code: String,
    pub display_name: String,
    #[serde(default = "founder_default")]
    pub is_founder: bool,
}

fn founder_default() -> bool {
    true
}

impl Household {
    pub fn new(name: String) -> Self {
        const ALPHABET: &[u8] = b"ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        let random = Uuid::new_v4();
        let pairing_code = random.as_bytes()[..6]
            .iter()
            .map(|b| char::from(ALPHABET[usize::from(b & 31)]))
            .collect();
        Self {
            household_id: Uuid::new_v4().to_string(),
            household_name: name,
            pairing_code,
            display_name: "Home Hub".into(),
            is_founder: true,
        }
    }
}

#[derive(Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Config {
    pub node_id: String,
    pub households: Vec<Household>,
    pub wall_board_port: u16,
    pub update_release_url: String,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            node_id: Uuid::new_v4().to_string(),
            households: Vec::new(),
            wall_board_port: 8765,
            update_release_url: DEFAULT_RELEASE_URL.into(),
        }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct LegacyConfig {
    node_id: String,
    #[serde(flatten)]
    household: Household,
    #[serde(default)]
    port: Option<u16>,
    #[serde(default)]
    update_release_url: String,
}

#[derive(Clone, Default, Deserialize, Serialize)]
#[serde(default, rename_all = "camelCase")]
pub struct Tables {
    pub peers: Vec<Row>,
    pub members: Vec<Row>,
    pub chores: Vec<Row>,
    pub completions: Vec<Row>,
    pub places: Vec<Row>,
    pub reminders: Vec<Row>,
    pub events: Vec<Row>,
    pub dinner_options: Vec<Row>,
    pub grocery_items: Vec<Row>,
}

impl Tables {
    fn tables_mut(&mut self) -> [&mut Vec<Row>; 8] {
        [
            &mut self.members,
            &mut self.chores,
            &mut self.completions,
            &mut self.places,
            &mut self.reminders,
            &mut self.events,
            &mut self.dinner_options,
            &mut self.grocery_items,
        ]
    }

    fn into_tables(self) -> [Vec<Row>; 8] {
        [
            self.members,
            self.chores,
            self.completions,
            self.places,
            self.reminders,
            self.events,
            self.dinner_options,
            self.grocery_items,
        ]
    }

    pub fn merge(
        &mut self,
        incoming: Self,
        id: &str,
        own_node: &str,
        sender: &str,
        endpoint: &str,
    ) {
        let now = now_ms();
        let mut peers = BTreeMap::new();
        for peer in self.peers.drain(..).filter_map(|peer| scoped(peer, id)) {
            let key = field(&peer, "nodeId").to_owned();
            if !key.is_empty() && key != own_node {
                peers.insert(key, peer);
            }
        }
        for peer in incoming
            .peers
            .iter()
            .cloned()
            .filter_map(|peer| scoped(peer, id))
        {
            let key = field(&peer, "nodeId").to_owned();
            if key.is_empty() || key == own_node {
                continue;
            }
            let previous = peers.get(&key);
            let mut chosen = if previous.is_none_or(|old| timestamp(&peer) > timestamp(old)) {
                peer
            } else if key == sender {
                previous.expect("peer already present").clone()
            } else {
                continue;
            };
            if key == sender {
                chosen.insert("lastSeenAt".into(), now.into());
                if !endpoint.is_empty() {
                    chosen.insert("endpointId".into(), endpoint.into());
                }
            }
            peers.insert(key, chosen);
        }
        self.peers = peers.into_values().collect();
        for (local, incoming) in self.tables_mut().into_iter().zip(incoming.into_tables()) {
            let mut rows = BTreeMap::new();
            for row in local
                .drain(..)
                .chain(incoming)
                .filter_map(|row| scoped(row, id))
            {
                let key = field(&row, "id").to_owned();
                if !key.is_empty()
                    && rows
                        .get(&key)
                        .is_none_or(|old| timestamp(&row) > timestamp(old))
                {
                    rows.insert(key, row);
                }
            }
            *local = rows.into_values().collect();
        }
    }
}

fn scoped(mut row: Row, id: &str) -> Option<Row> {
    if row.get("householdId").is_some_and(|value| {
        value
            .as_str()
            .is_none_or(|actual| !actual.is_empty() && actual != id)
    }) {
        return None;
    }
    row.insert("householdId".into(), id.into());
    Some(row)
}

pub fn field<'a>(row: &'a Row, key: &str) -> &'a str {
    row.get(key).and_then(Value::as_str).unwrap_or_default()
}

pub fn flag(row: &Row, key: &str) -> bool {
    row.get(key).and_then(Value::as_bool).unwrap_or(false)
}

fn timestamp(row: &Row) -> i64 {
    row.get("updatedAt")
        .and_then(Value::as_i64)
        .unwrap_or_default()
}

pub fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

pub fn pairing_code_matches(expected: &str, provided: &str) -> bool {
    use sha2::{Digest, Sha256};
    bool::from(Sha256::digest(expected).ct_eq(&Sha256::digest(provided)))
}

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Snapshot {
    pub protocol_version: u32,
    pub household_id: String,
    pub household_name: String,
    pub sender_node_id: String,
    pub sender_endpoint_id: String,
    pub sent_at: u64,
    #[serde(default)]
    pub hub_endpoint_id: String,
    pub update_release_url: String,
    #[serde(flatten)]
    pub tables: Tables,
}

impl Snapshot {
    pub fn from_store(
        house: &Household,
        config: &Config,
        endpoint_id: &str,
        version: &str,
        store: &Tables,
    ) -> Self {
        let id = &house.household_id;
        let mut tables = store.clone();
        let now = now_ms();
        tables.peers.push(
            match json!({
                "householdId": id, "nodeId": config.node_id, "endpointId": endpoint_id,
                "memberId": format!("hub_{}", config.node_id),
                "displayName": house.display_name, "role": "PARENT", "deviceType": "WINDOWS",
                "isFounder": house.is_founder, "appVersionCode": 1, "appVersionName": version,
                "lastSeenAt": now, "updatedAt": now
            }) {
                Value::Object(row) => row,
                _ => unreachable!(),
            },
        );
        Self {
            protocol_version: 2,
            household_id: id.clone(),
            household_name: house.household_name.clone(),
            sender_node_id: config.node_id.clone(),
            sender_endpoint_id: endpoint_id.into(),
            sent_at: now,
            hub_endpoint_id: endpoint_id.into(),
            update_release_url: config.update_release_url.clone(),
            tables,
        }
    }
}

pub fn safe_household_id(id: &str) -> bool {
    !id.is_empty()
        && id.len() <= 128
        && id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
}

fn safe_pairing_code(code: &str) -> bool {
    (4..=128).contains(&code.len())
        && code
            .bytes()
            .all(|b| b.is_ascii_uppercase() || b.is_ascii_digit())
}

fn validate_config(config: &Config) -> Result<()> {
    ensure!(!config.node_id.is_empty(), "The hub node ID is empty");
    let mut ids = std::collections::HashSet::new();
    for house in &config.households {
        ensure!(
            safe_household_id(&house.household_id),
            "Invalid household ID in hub config"
        );
        ensure!(
            safe_pairing_code(&house.pairing_code),
            "Invalid household pairing code in hub config"
        );
        ensure!(
            ids.insert(house.household_id.to_ascii_lowercase()),
            "Duplicate household ID in hub config"
        );
    }
    Ok(())
}

pub fn load_or_migrate_config(dir: &Path) -> Result<Config> {
    let path = dir.join(CONFIG_FILE);
    let Some(raw): Option<Value> = load_json(&path)? else {
        return Ok(Config::default());
    };
    if raw.get("households").is_some() {
        let config = serde_json::from_value(raw).context("Reading hub configuration")?;
        validate_config(&config)?;
        return Ok(config);
    }
    let legacy: LegacyConfig =
        serde_json::from_value(raw).context("Reading legacy hub configuration")?;
    let mut house = legacy.household;
    house.pairing_code = house.pairing_code.trim().to_ascii_uppercase();
    let config = Config {
        node_id: legacy.node_id,
        households: vec![house],
        wall_board_port: legacy.port.unwrap_or(8765),
        update_release_url: if legacy.update_release_url.is_empty() {
            DEFAULT_RELEASE_URL.into()
        } else {
            legacy.update_release_url
        },
    };
    validate_config(&config)?;
    let id = &config.households[0].household_id;
    let destination = store_path(dir, id);
    if !destination.try_exists()? {
        let legacy_file = dir.join(LEGACY_DATA_FILE);
        if let Some(raw) = load_json::<Value>(&legacy_file)? {
            ensure!(
                raw.get("householdId")
                    .and_then(Value::as_str)
                    .is_none_or(|old_id| old_id.is_empty() || old_id == id),
                "Legacy hub data belongs to another household"
            );
            let mut tables = Tables::default();
            tables.merge(serde_json::from_value(raw)?, id, &config.node_id, "", "");
            save_json(&destination, &tables)?;
        }
    }
    let backup = dir.join("homelist-hub-config-v1.json");
    if !backup.try_exists()? {
        write_atomic(&backup, &fs::read(&path)?)?;
    }
    save_config(dir, &config)?;
    Ok(config)
}

pub fn save_config(dir: &Path, config: &Config) -> Result<()> {
    validate_config(config)?;
    save_json(&dir.join(CONFIG_FILE), config)
}

pub fn store_path(dir: &Path, id: &str) -> PathBuf {
    dir.join(format!("homelist-hub-data-{id}.json"))
}

pub fn load_json<T: DeserializeOwned>(path: &Path) -> Result<Option<T>> {
    let data = match fs::read(path) {
        Ok(data) => data,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(error).with_context(|| format!("Reading {}", path.display())),
    };
    let data = data.strip_prefix(b"\xEF\xBB\xBF").unwrap_or(&data);
    serde_json::from_slice(data)
        .map(Some)
        .with_context(|| format!("Parsing {}", path.display()))
}

pub fn save_json(path: &Path, value: &impl Serialize) -> Result<()> {
    write_atomic(path, &serde_json::to_vec(value)?)
}

pub fn load_identity(dir: &Path) -> Result<SecretKey> {
    let path = dir.join("homelist-hub-key.bin");
    match fs::read(&path) {
        Ok(bytes) => {
            let key: [u8; 32] = bytes
                .try_into()
                .map_err(|_| anyhow::anyhow!("Hub identity file must contain 32 bytes"))?;
            Ok(SecretKey::from_bytes(&key))
        }
        Err(error) if error.kind() == io::ErrorKind::NotFound => {
            let key = SecretKey::generate();
            write_atomic(&path, &key.to_bytes())?;
            Ok(key)
        }
        Err(error) => Err(error).context("Reading the hub identity"),
    }
}

fn write_atomic(path: &Path, data: &[u8]) -> Result<()> {
    let parent = path.parent().context("Missing parent directory")?;
    let mut file = tempfile::NamedTempFile::new_in(parent)?;
    file.write_all(data)?;
    file.as_file().sync_all()?;
    file.persist(path)
        .with_context(|| format!("Replacing {}", path.display()))?;
    #[cfg(unix)]
    fs::File::open(parent)?.sync_all()?;
    Ok(())
}
