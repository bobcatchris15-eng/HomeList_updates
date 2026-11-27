use crate::{
    Hub,
    store::{field, flag},
};
use axum::{
    Json, Router,
    extract::{Query, State},
    http::{HeaderMap, StatusCode, header},
    response::Html,
    routing::get,
};
use chrono::{Datelike, Local};
use serde::Deserialize;
use serde_json::{Value, json};
use std::sync::Arc;

#[derive(Clone)]
struct Wall {
    hub: Arc<Hub>,
    port: u16,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Selection {
    household_id: Option<String>,
}

pub(crate) fn router(hub: Arc<Hub>, port: u16) -> Router {
    Router::new()
        .route("/", get(page))
        .route("/wall", get(page))
        .route("/api/v1/wall-households", get(households))
        .route("/api/v1/wall-snapshot", get(snapshot))
        .with_state(Wall { hub, port })
}

fn local(headers: &HeaderMap, port: u16) -> Result<(), StatusCode> {
    let host = headers
        .get(header::HOST)
        .and_then(|value| value.to_str().ok());
    match host {
        Some(host)
            if host == format!("localhost:{port}") || host == format!("127.0.0.1:{port}") =>
        {
            Ok(())
        }
        _ => Err(StatusCode::FORBIDDEN),
    }
}

async fn page(
    State(wall): State<Wall>,
    headers: HeaderMap,
) -> Result<Html<&'static str>, StatusCode> {
    local(&headers, wall.port)?;
    Ok(Html(include_str!("wall.html")))
}

async fn households(
    State(wall): State<Wall>,
    headers: HeaderMap,
) -> Result<Json<Value>, StatusCode> {
    local(&headers, wall.port)?;
    Ok(Json(json!(
        wall.hub
            .config
            .households
            .iter()
            .map(|house| json!({
                "householdId": house.household_id, "householdName": house.household_name,
            }))
            .collect::<Vec<_>>()
    )))
}

async fn snapshot(
    State(wall): State<Wall>,
    headers: HeaderMap,
    Query(selection): Query<Selection>,
) -> Result<Json<Value>, StatusCode> {
    local(&headers, wall.port)?;
    let house = match selection.household_id {
        Some(id) => wall
            .hub
            .config
            .households
            .iter()
            .find(|house| house.household_id == id),
        None => wall.hub.config.households.first(),
    }
    .ok_or(StatusCode::NOT_FOUND)?;
    let data = wall.hub.stores[&house.household_id].lock().await;
    let now = Local::now();
    let day = now.format("%Y-%m-%d").to_string();
    let iso_day = now.weekday().number_from_monday();
    let chores = data
        .chores
        .iter()
        .filter(|row| {
            if flag(row, "isDeleted") {
                return false;
            }
            if row
                .get("isRecurring")
                .and_then(Value::as_bool)
                .unwrap_or(true)
            {
                let schedule = field(row, "daysOfWeekCsv");
                return schedule.is_empty()
                    || schedule
                        .split(',')
                        .any(|day| day.trim().parse() == Ok(iso_day));
            }
            let due = field(row, "dueDateKey");
            if !due.is_empty() && due > day.as_str() {
                return false;
            }
            !data.completions.iter().any(|completion| {
                field(completion, "choreId") == field(row, "id")
                    && flag(completion, "isCompleted")
                    && field(completion, "dateKey") < day.as_str()
            })
        })
        .collect::<Vec<_>>();
    let completed = |id: &str| {
        data.completions.iter().any(|row| {
            field(row, "choreId") == id && field(row, "dateKey") == day && flag(row, "isCompleted")
        })
    };
    let summaries = data.members.iter().filter(|row| !flag(row, "isDeleted")).map(|row| {
        let assigned = chores.iter().filter(|chore| field(chore, "assignedToMemberId") == field(row, "id")).collect::<Vec<_>>();
        json!({
            "displayName": field(row, "displayName"),
            "todayCompleted": assigned.iter().filter(|chore| completed(field(chore, "id"))).count(),
            "todayTotal": assigned.len(),
        })
    }).collect::<Vec<_>>();
    let meals = data.dinner_options.iter().filter(|row| {
        !flag(row, "isDeleted")
            && field(row, "dateKey") == day
            && matches!(field(row, "mealSlot"), "" | "DINNER")
    });
    let dinner = meals.clone().find(|row| flag(row, "isWinner")).or_else(|| {
        meals.max_by_key(|row| {
            field(row, "voterIdsCsv")
                .split(',')
                .filter(|vote| !vote.is_empty())
                .count()
        })
    });
    let mut upcoming = data
        .events
        .iter()
        .filter(|row| !flag(row, "isDeleted") && field(row, "dateKey") >= day.as_str())
        .collect::<Vec<_>>();
    upcoming.sort_by_key(|row| {
        (
            field(row, "dateKey"),
            row.get("startHour").and_then(Value::as_u64),
        )
    });
    Ok(Json(json!({
        "householdName": house.household_name,
        "todayDateLabel": now.format("%A, %b %-d").to_string(),
        "overallCompleted": chores.iter().filter(|row| completed(field(row, "id"))).count(),
        "overallTotal": chores.len(),
        "peerCount": data.peers.len(),
        "tonightDinner": dinner.map(|row| field(row, "mealName")),
        "members": summaries,
        "upcomingEvents": upcoming.iter().take(10).map(|row| json!({
            "dateKey": field(row, "dateKey"),
            "title": field(row, "title"),
            "location": field(row, "location"),
            "timeLabel": if flag(row,"allDay") { "All day".to_string() } else {
                format!("{:02}:{:02}", row.get("startHour").and_then(Value::as_u64).unwrap_or(0),
                    row.get("startMinute").and_then(Value::as_u64).unwrap_or(0))
            }
        })).collect::<Vec<_>>(),
    })))
}
