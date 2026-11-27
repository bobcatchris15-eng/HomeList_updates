use reqwest::Client;
use semver::Version;
use serde::Deserialize;
use std::time::Duration;
use url::Url;

#[derive(Deserialize)]
struct Release {
    tag_name: String,
    assets: Vec<Asset>,
}

#[derive(Deserialize)]
struct Asset {
    name: String,
}

fn latest_api(release_url: &str) -> Option<Url> {
    let url = Url::parse(release_url).ok()?;
    if url.scheme() != "https" || url.host_str() != Some("github.com") {
        return None;
    }
    let parts = url
        .path_segments()?
        .filter(|segment| !segment.is_empty())
        .collect::<Vec<_>>();
    if !matches!(
        parts.as_slice(),
        [_, _] | [_, _, "releases"] | [_, _, "releases", "latest"]
    ) || parts[..2].iter().any(|part| {
        !part
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b"._-".contains(&b))
    }) {
        return None;
    }
    Url::parse(&format!(
        "https://api.github.com/repos/{}/{}/releases/latest",
        parts[0], parts[1]
    ))
    .ok()
}

pub(crate) async fn check_daily(release_url: String, installed: String) {
    let Some(api) = latest_api(&release_url) else {
        return;
    };
    let Ok(installed) = Version::parse(installed.trim_start_matches(['v', 'V'])) else {
        return;
    };
    let _ = rustls::crypto::ring::default_provider().install_default();
    let Ok(client) = Client::builder()
        .user_agent("HomeList-Windows-Hub")
        .timeout(Duration::from_secs(15))
        .build()
    else {
        return;
    };
    let mut interval = tokio::time::interval(Duration::from_secs(24 * 60 * 60));
    loop {
        interval.tick().await;
        let Ok(response) = client
            .get(api.clone())
            .send()
            .await
            .and_then(reqwest::Response::error_for_status)
        else {
            continue;
        };
        let Ok(release) = response.json::<Release>().await else {
            continue;
        };
        let Ok(latest) = Version::parse(release.tag_name.trim_start_matches(['v', 'V'])) else {
            continue;
        };
        if latest > installed
            && release
                .assets
                .iter()
                .any(|asset| asset.name == format!("HomeList-Windows-Hub-{}.zip", release.tag_name))
        {
            println!(
                "HomeList Hub {latest} is available. Stop the hub and run Update-HomeListHub.bat to install it."
            );
        }
    }
}
