# WIZALCH — OSRS High Alchemy Intelligence & Profit Scanner

**WIZALCH** is a RuneLite sidebar plugin for Old School RuneScape (OSRS) that retrieves real-time Grand Exchange market data, calculates net High Level Alchemy margins across all tradeable OSRS items, evaluates 4-hour Grand Exchange buying limits, tracks custom watchlists with profit alerts, and ranks the best items to buy and high alch right now.

> **Informational Only:** WIZALCH is strictly a market analysis and decision-support tool. It never automates gameplay, casts spells, interacts with the Grand Exchange, or performs any in-game actions.

---

## Feature Overview

### 1. Overview Dashboard
- **Live Data Status Indicator:** Displays whether market data is `LIVE DATA`, `USING CACHE`, `REFRESHING...`, or `DATA UNAVAILABLE`.
- **Market Telemetry:** Shows last successful sync time, active Nature rune price (`ID 561`), total scanned items, and active data source.
- **4 Key Metric Cards:**
  - **Most Profitable Alch:** Highest net profit per cast among liquid, non-stale items.
  - **Best ROI:** Highest return-on-investment percentage.
  - **Profitable Items:** Total count of items currently yielding a positive net profit.
  - **Best 4h Bulk Opportunity:** Highest theoretical 4-hour Grand Exchange buy-limit cycle profit.
- **Top 10 Best Items to High Alch:** Ranked quick-access cards with 1-click navigation to detailed item analysis.

### 2. High Alch Profit & ROI Calculations
Every item is evaluated using overflow-safe 64-bit integer GP arithmetic:
- **Total Cost per Cast:**
  $$\text{Total Cost} = \text{Estimated Purchase Price} + \text{Nature Rune Cost} + \text{Fire Rune Cost}$$
- **Net Profit per Alch:**
  $$\text{Net Profit per Alch} = \text{High Alch Value} - \text{Total Cost}$$
- **Return on Investment (ROI %):**
  $$\text{ROI (\%)} = \left(\frac{\text{Net Profit per Alch}}{\text{Total Cost}}\right) \times 100$$
- **Break-Even Buy Price:**
  $$\text{Break-Even Price} = \text{High Alch Value} - \text{Nature Rune Cost} - \text{Fire Rune Cost}$$
- **Hourly Profit Projection:** Modelled at the standard High Level Alchemy casting rate of `1,200 casts/hour` (5 ticks per cast).
- **No GE Seller Tax:** Grand Exchange seller tax is intentionally excluded because High Level Alchemy converts items directly into coins rather than selling on the Grand Exchange.

### 3. Grand Exchange Price Information & Nature Rune Pricing
- **Multi-Horizon GE Pricing:** Supports 5 configurable purchase price estimation modes:
  1. **Instant Buy (Latest High)** — Default realistic instant-buy price from `/latest` (with automatic fallback to 1h/5m average if missing).
  2. **Stable 1h Average** — Volume-weighted 1-hour average buy price from `/1h`.
  3. **Recent 5m Average** — Rolling 5-minute average buy price from `/5m`.
  4. **Conservative (Max of High & 1h)** — Uses the higher of Instant Buy and 1h Average for safer margin estimates.
  5. **Patient Buy Offer (Latest Low)** — Models slow-buying items via instant-sell offers.
- **Live & Manual Nature Rune Pricing:** Automatically tracks the live Grand Exchange price of **Nature runes (`ID 561`)** and **Fire runes (`ID 554`)**, with support for manual GP overrides and a toggle for an equipped Fire staff (`0 GP` fire rune cost).

### 4. Live Item Scanner & Filtering
- **8 Sorting Modes:**
  - Highest Profit / Alch
  - Highest ROI %
  - Highest Profit / 100 Alchs
  - Highest Profit / 1,000 Alchs
  - Highest Volume (1h)
  - Lowest Investment
  - Highest Alch Value
  - Highest GE Limit (4h)
- **Smart Recommendation Categories:** Filter by *Best Overall*, *Best Profit / Alch*, *Best ROI*, *Best Bulk Alch*, *Lowest Investment*, *Highest Volume*, or *Lowest Risk*.
- **Advanced Filter Drawer:** Filter by minimum profit presets (`100 GP` to `5,000 GP` or custom), minimum ROI %, maximum unit buy price, minimum 1h volume, minimum 4h GE buy limit, membership status (`All`, `Members P2P`, `Free-to-Play F2P`), and toggles to hide losses, stale data, or inactive items.
- **Dual View Modes:** Switch seamlessly between **Card View** and **Compact Table View**.

### 5. Detailed Item Analysis & Market Trend Graph
- Full item breakdown showing High Alch value, Low Alch value, Instant-Buy, Instant-Sell, 1h Stable Average, 5m Average, Break-Even price, and 1h trading volume.
- **4-Hour GE Buy Limit Intelligence:** Computes full 4-hour cycle capital required, theoretical 4-hour net profit, Nature runes required, and displays a **Low Supply Warning** whenever estimated 4-hour market volume is lower than the item's 4-hour GE buy limit.
- **Historical Price Trend Chart:** Queries `/timeseries` to plot recent buy/sell price movement against the dashed gold break-even threshold and classifies the margin as **Improving**, **Declining**, or **Stable**.
- **Risk & Safety Warnings:** Flags items with `LOW VOLUME`, `PRICE UNSTABLE`, `STALE DATA`, `HIGH INVESTMENT`, `POSSIBLE PRICE MANIPULATION`, or `INSUFFICIENT DATA`.

### 6. Dedicated Profit & Quantity Calculator
- Batch presets for `1`, `10`, `25`, `50`, `100`, `250`, `500`, `1,000`, `4h GE Limit`, or any custom quantity.
- **Target Profit & Budget Reverse Calculator:** Computes the maximum purchase price to achieve a target profit per alch and the maximum affordable buy price for a given GP budget.

### 7. Watchlist & Profit Alerts
- Pin favourite items to a persistent watchlist stored via RuneLite's `ConfigManager`.
- Configure per-item target profit (GP) and minimum ROI (%) thresholds.
- Optional native RuneLite notifications (`Notifier`) when watched items exceed your target profit/ROI, turn profitable, or drop below your floor, with configurable cooldowns to prevent notification spam.

---

## External Data Sources & Privacy Disclosure

WIZALCH retrieves publicly available Grand Exchange market data from the official **OSRS Wiki Real-Time Prices API** (`https://prices.runescape.wiki/api/v1/osrs`), operated in partnership with RuneLite:

| Endpoint | Purpose |
| :--- | :--- |
| `GET /api/v1/osrs/mapping` | Item IDs, names, members status, high/low alchemy values, and 4h GE buying limits |
| `GET /api/v1/osrs/latest` | Latest instant-buy (`high`) and instant-sell (`low`) prices and trade timestamps |
| `GET /api/v1/osrs/5m` | 5-minute rolling average prices and trade volumes |
| `GET /api/v1/osrs/1h` | 1-hour average prices and trade volumes |
| `GET /api/v1/osrs/timeseries` | Historical price time-series for the selected item in the Analysis view |

- **Privacy Guarantee:** WIZALCH only sends standard HTTP `GET` requests with a descriptive `User-Agent` header (`WIZALCH-RuneLite-Plugin/1.0 (+https://github.com/runelite/plugin-hub)`). **Zero personal data, account names, IP identifiers, or in-game player state are ever collected or transmitted.**
- **Asynchronous & Cached:** All network calls run on background threads via RuneLite's shared `OkHttpClient` and are cached locally in `.runelite/cache/alchmate-pro/` to respect API rate limits.

---

## Installation

1. Open **RuneLite**.
2. Click the **Configuration** wrench icon in the sidebar and open the **Plugin Hub** at the bottom of the plugin list.
3. Search for **WIZALCH** and click **Install**.
4. Click the gold **WIZALCH** diamond icon in the RuneLite sidebar toolbar to open the scanner.

---

## Configuration Options

All settings can be adjusted either from RuneLite's plugin settings panel or inside the **Settings** tab of the WIZALCH sidebar:

- **Use Live Nature Rune Price:** Automatically use the live GE price of Nature runes (`ID 561`) or use a custom manual GP override.
- **Manual Nature Rune Price (GP):** Fallback or manual cost per Nature rune (default `150 GP`).
- **Equipped Fire Staff (0 GP Fire Runes):** Assume a Fire staff, Tome of fire, or Bryophyta's staff is equipped (`true` by default).
- **Price Calculation Method:** Choose between *Instant Buy*, *Stable 1h Average*, *Recent 5m Average*, *Conservative*, or *Patient Buy Offer*.
- **Maximum Data Age (Minutes):** Stale trade age threshold (`120 minutes` default).
- **Auto-Refresh Market Data & Refresh Interval:** Automatically refresh market prices in the background (`60 seconds` default, minimum `30 seconds`).
- **Default Minimum Profit / ROI / Volume Filters:** Set default scanner filter thresholds.
- **Enable Watchlist Alerts & Cooldown:** Toggle native RuneLite notifications for watched items and set the per-item cooldown timer.

---

## Known Limitations

- **Grand Exchange Real-Time Price Latency:** Prices reflect trades reported to the OSRS Wiki Real-Time Prices API by RuneLite clients and may differ slightly from unfulfilled in-game Grand Exchange offers for thinly traded items.
- **4-Hour Buy Limits:** Grand Exchange buy limits represent the maximum quantity purchasable per 4-hour window on a single account; actual order completion depends on market supply and liquidity.
- **No Unalchable Items:** Untradeable items and items that cannot be cast with High Level Alchemy are automatically excluded from scanning.

---

## License

**WIZALCH** is licensed under the [BSD 2-Clause License](LICENSE).
