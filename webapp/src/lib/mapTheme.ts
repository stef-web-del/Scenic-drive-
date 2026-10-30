// GENERIERT aus design/tokens.json (app/tools/gen_tokens.py) – nicht von Hand ändern.
export const MAP_THEME = {
  "light": {
    "land": "#F5F6FA",
    "water": "#C9D6EA",
    "forest": "#D3DED6",
    "road": "#FFFFFF",
    "roadMajor": "#E0E3EC",
    "label": "#4A5370",
    "routeActive": "#9C6618",
    "routeCasing": "#1A1A2E",
    "routeAlt": "#757D94",
    "approach": "#0F3460"
  },
  "dark": {
    "land": "#1A1A2E",
    "water": "#0F3460",
    "forest": "#1D2F3A",
    "road": "#2E3756",
    "roadMajor": "#414C70",
    "label": "#B8C1D6",
    "routeActive": "#EFC07B",
    "routeCasing": "#0B0B16",
    "routeAlt": "#7F8BA8",
    "approach": "#ECEEF4"
  }
} as const
export const SCORE_STOPS: ReadonlyArray<readonly [number, string]> = [[0.15, "#3E5F9A"], [0.18, "#7A8DB0"], [0.21, "#C9A46E"], [0.24, "#EFC07B"]]
export const SPEED_STOPS: ReadonlyArray<string> = ["#3E5F9A", "#7A8DB0", "#C9A46E", "#EFC07B", "#B3261E"]
export const ROUTE = {"routeWidth": 6, "routeCasing": 2, "altWidth": 4, "approachDash": [2, 2]} as const
