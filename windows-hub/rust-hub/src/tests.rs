use super::*;
use serde_json::json;

fn row(value: serde_json::Value) -> store::Row {
    value.as_object().unwrap().clone()
}

#[test]
fn legacy_store_preserves_identity_and_tombstones() -> Result<()> {
    let dir = tempfile::tempdir()?;
    let original = json!({
        "nodeId": "node-v1", "householdId": "house-v1", "householdName": "Original",
        "pairingCode": "ABC234", "displayName": "Hub", "isFounder": true, "port": 8765
    });
    store::save_json(&dir.path().join("homelist-hub-config.json"), &original)?;
    store::save_json(
        &dir.path().join("homelist-hub-data.json"),
        &json!({
            "householdId": "house-v1",
            "chores": [{"id":"old-chore", "title":"Old", "updatedAt": 25, "isDeleted": true}],
            "groceryItems": [{"id":"milk", "name":"Milk", "updatedAt": 1}]
        }),
    )?;
    let config = store::load_or_migrate_config(dir.path())?;
    assert_eq!(config.node_id, "node-v1");
    assert_eq!(config.households[0].pairing_code, "ABC234");
    assert_eq!(
        store::load_json::<serde_json::Value>(&dir.path().join("homelist-hub-config-v1.json"))?,
        Some(original)
    );
    let identity = store::load_identity(dir.path())?.public();
    assert_eq!(store::load_identity(dir.path())?.public(), identity);
    let reopened = store::load_or_migrate_config(dir.path())?;
    let hub = Hub::open(
        dir.path().to_path_buf(),
        reopened,
        identity.to_string(),
        "1.0.0".into(),
    )?;
    let tables = hub.stores["house-v1"].try_lock()?;
    assert!(store::flag(&tables.chores[0], "isDeleted"));
    assert_eq!(store::field(&tables.chores[0], "householdId"), "house-v1");
    assert_eq!(store::field(&tables.grocery_items[0], "name"), "Milk");
    Ok(())
}

async fn dial(client: &Endpoint, server: &Endpoint, request: &Request) -> Result<Response> {
    timeout(EXCHANGE_TIMEOUT, async {
        let connection = client.connect(server.addr(), ALPN).await?;
        let (mut writer, mut reader) = connection.open_bi().await?;
        write_frame(&mut writer, request).await?;
        read_frame(&mut reader).await
    })
    .await?
}

#[tokio::test]
async fn encrypted_exchange_authenticates_and_isolates_stores() -> Result<()> {
    let dir = tempfile::tempdir()?;
    let server = Endpoint::builder(presets::Minimal)
        .alpns(vec![ALPN.to_vec()])
        .bind()
        .await?;
    let client = Endpoint::builder(presets::Minimal).bind().await?;
    let a = Household::new("House A".into());
    let b = Household::new("House B".into());
    let config = Config {
        households: vec![a.clone(), b.clone()],
        ..Config::default()
    };
    let hub = Arc::new(Hub::open(
        dir.path().to_path_buf(),
        config,
        server.id().to_string(),
        "1.0.0".into(),
    )?);
    let receiving = tokio::spawn({
        let server = server.clone();
        let hub = hub.clone();
        async move { serve(&server, hub).await }
    });

    let client_id = client.id().to_string();
    let tables = Tables {
        chores: vec![
            row(json!({"id": "same-id", "title": "A chore", "updatedAt": 30, "isDeleted": true})),
            row(
                json!({"id": "foreign", "householdId": b.household_id, "title": "Wrong household", "updatedAt": 55}),
            ),
        ],
        ..Tables::default()
    };
    let payload = Snapshot {
        protocol_version: 2,
        household_id: a.household_id.clone(),
        household_name: a.household_name,
        sender_node_id: "test-node".into(),
        sender_endpoint_id: client_id,
        sent_at: 0,
        update_release_url: String::new(),
        hub_endpoint_id: String::new(),
        tables,
    };
    let mut request = Request {
        protocol_version: 2,
        household_id: a.household_id.clone(),
        pairing_code: b.pairing_code.clone() + "X",
        payload: Some(payload),
    };
    assert_eq!(
        dial(&client, &server, &request).await?.error.as_deref(),
        Some("invalid_pairing_code")
    );
    assert!(hub.stores[&a.household_id].lock().await.chores.is_empty());
    let before: Tables =
        store::load_json(&store::store_path(dir.path(), &a.household_id))?.unwrap();
    assert!(before.chores.is_empty());
    assert_eq!(store::field(&before.places[0], "name"), "Home");

    request.pairing_code = a.pairing_code.clone();
    let response = dial(&client, &server, &request).await?;
    assert!(response.ok);
    let received = response.payload.unwrap();
    assert_eq!(received.sender_endpoint_id, server.id().to_string());
    assert_eq!(received.tables.chores.len(), 1);
    assert!(store::flag(&received.tables.chores[0], "isDeleted"));
    assert_eq!(
        store::field(&received.tables.chores[0], "householdId"),
        a.household_id
    );
    assert!(hub.stores[&b.household_id].lock().await.chores.is_empty());

    request.payload.as_mut().unwrap().tables.chores = vec![row(
        json!({"id": "same-id", "title": "Stale", "updatedAt": 29}),
    )];
    assert!(dial(&client, &server, &request).await?.ok);
    let disk: Tables = store::load_json(&store::store_path(dir.path(), &a.household_id))?.unwrap();
    assert!(store::flag(&disk.chores[0], "isDeleted"));

    request.household_id = "unknown".into();
    request.payload = None;
    let unknown = dial(&client, &server, &request).await?;
    assert_eq!(unknown.error.as_deref(), Some("unknown_household"));
    assert!(unknown.payload.is_none());

    // A bogus length is rejected without allocating the frame or writing storage.
    let connection = client.connect(server.addr(), ALPN).await?;
    let (mut send, mut recv) = connection.open_bi().await?;
    send.write_all(&((MAX_FRAME_BYTES as u32) + 1).to_be_bytes())
        .await?;
    send.finish()?;
    let malformed: Response = read_frame(&mut recv).await?;
    assert_eq!(malformed.error.as_deref(), Some("bad_request"));
    drop(connection);
    client.close().await;
    server.close().await;
    receiving.await?;
    Ok(())
}
