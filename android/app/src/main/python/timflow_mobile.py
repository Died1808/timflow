"""TIMFLOW mobile wrapper for Android integration via Chaquopy.

Provides a simplified interface to TIMFLOW groundwater modeling that can
be called from Kotlin/Android using the Chaquopy Python runtime.

The main entry point is :func:`run_timflow`, which accepts a pandas DataFrame
of hydrogeological parameters and returns a GeoJSON string of head-lowering
contour lines around the pumping well.

Example usage from Kotlin (Chaquopy)::

    val py = Python.getInstance()
    val module = py.getModule("timflow_mobile")
    val geojson = module.callAttr("run_timflow", dataFrameJsonStr).toString()
"""

from __future__ import annotations

import json
import math

import numpy as np
import pandas as pd

# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

__all__ = ["run_timflow", "run_timflow_from_json", "nearest_record"]


def run_timflow(df: pd.DataFrame) -> str:
    """Compute groundwater-lowering contours and return as GeoJSON.

    Parameters
    ----------
    df : pd.DataFrame
        Single-row DataFrame containing hydro parameters for the pumping
        well.  Required columns:

        * ``lat`` – well latitude (decimal degrees WGS-84)
        * ``lon`` – well longitude (decimal degrees WGS-84)
        * ``kaq`` – aquifer hydraulic conductivity [m/d]
        * ``H`` – aquifer saturated thickness [m]
        * ``c`` – aquitard resistance [d] (use 0 for unconfined / single layer)
        * ``Qw`` – pumping rate [m³/d]  (positive = extraction)
        * ``rw`` – well radius [m]
        * ``t``  – simulation time [d] (transient) or 0 for steady-state

        Optional columns:

        * ``contour_levels`` – comma-separated lowering values [m]  (default: "0.1,0.5,1,2,5")
        * ``grid_m``         – half-width of computation grid [m]   (default: 2000)
        * ``ngr``            – number of grid points per axis        (default: 50)

    Returns
    -------
    str
        GeoJSON FeatureCollection string containing:

        * ``type: "Feature"`` for each contour level with ``geometry``
          (MultiLineString) and ``properties.lowering`` (m).
        * A ``type: "Feature"`` for the well location (Point).

    Raises
    ------
    ValueError
        If required columns are missing or parameter values are invalid.
    KeyError
        If ``df`` is empty.
    """
    row = _extract_row(df)
    return _compute_contours(row)


def run_timflow_from_json(json_str: str) -> str:
    """Compute groundwater-lowering contours from a JSON-encoded record.

    Convenience wrapper for :func:`run_timflow` that accepts a JSON string
    instead of a DataFrame.  Useful when calling from Kotlin via Chaquopy
    without needing pandas interop.

    Parameters
    ----------
    json_str : str
        JSON object string with the same keys as the DataFrame columns
        described in :func:`run_timflow`.

    Returns
    -------
    str
        GeoJSON FeatureCollection string (same as :func:`run_timflow`).
    """
    record = json.loads(json_str)
    df = pd.DataFrame([record])
    return run_timflow(df)


def nearest_record(
    lat: float,
    lon: float,
    records_json: str,
) -> str:
    """Find the nearest hydro-parameter record to the given coordinates.

    Uses the Haversine formula to compute great-circle distances.

    Parameters
    ----------
    lat : float
        Observer latitude in decimal degrees (WGS-84).
    lon : float
        Observer longitude in decimal degrees (WGS-84).
    records_json : str
        JSON array of records, each with ``lat`` and ``lon`` fields
        plus hydrogeological parameters.

    Returns
    -------
    str
        JSON string of the single nearest record.
    """
    records = json.loads(records_json)
    if not records:
        raise ValueError("records_json must contain at least one record")

    best_idx = 0
    best_dist = float("inf")
    for i, rec in enumerate(records):
        d = _haversine(lat, lon, rec["lat"], rec["lon"])
        if d < best_dist:
            best_dist = d
            best_idx = i

    return json.dumps(records[best_idx])


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------

_REQUIRED_COLUMNS = {"lat", "lon", "kaq", "H", "Qw", "rw"}
_EARTH_RADIUS_M = 6_371_000.0
# Degrees of latitude per metre (approximate)
_DEG_PER_M_LAT = 1.0 / 111_320.0


def _extract_row(df: pd.DataFrame) -> dict:
    """Validate the DataFrame and return the first row as a dict."""
    if df.empty:
        raise KeyError("DataFrame is empty – at least one row is required")
    missing = _REQUIRED_COLUMNS - set(df.columns)
    if missing:
        raise ValueError(f"Missing required columns: {sorted(missing)}")
    return df.iloc[0].to_dict()


def _haversine(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Return great-circle distance [m] between two WGS-84 points."""
    r = _EARTH_RADIUS_M
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def _compute_contours(row: dict) -> str:
    """Run TIMFLOW and return a GeoJSON FeatureCollection string."""
    # Parse parameters
    lat: float = float(row["lat"])
    lon: float = float(row["lon"])
    kaq: float = float(row["kaq"])
    H: float = float(row["H"])
    c: float = float(row.get("c", 0))
    Qw: float = float(row["Qw"])
    rw: float = float(row["rw"])
    t: float = float(row.get("t", 0))
    grid_m: float = float(row.get("grid_m", 2000))
    ngr: int = int(row.get("ngr", 50))
    raw_levels = str(row.get("contour_levels", "0.1,0.5,1,2,5"))
    levels = [float(v) for v in raw_levels.split(",") if v.strip()]

    if kaq <= 0:
        raise ValueError(f"kaq must be positive, got {kaq}")
    if H <= 0:
        raise ValueError(f"H must be positive, got {H}")
    if rw <= 0:
        raise ValueError(f"rw must be positive, got {rw}")
    if not levels:
        raise ValueError("contour_levels must contain at least one value")

    # Build and solve TIMFLOW model
    lowering_grid, x_grid, y_grid = _run_timflow_model(kaq, H, c, Qw, rw, t, grid_m, ngr)

    # Convert local metric grid to WGS-84 for GeoJSON
    features = []

    # Contour lines
    contour_features = _extract_contour_geojson(
        lowering_grid, x_grid, y_grid, lat, lon, levels
    )
    features.extend(contour_features)

    # Well point
    features.append(
        {
            "type": "Feature",
            "geometry": {"type": "Point", "coordinates": [lon, lat]},
            "properties": {"type": "well", "Qw": Qw, "rw": rw},
        }
    )

    geojson = {
        "type": "FeatureCollection",
        "features": features,
        "metadata": {
            "kaq": kaq,
            "H": H,
            "c": c,
            "Qw": Qw,
            "rw": rw,
            "t": t,
            "grid_m": grid_m,
        },
    }
    return json.dumps(geojson)


def _run_timflow_model(
    kaq: float,
    H: float,
    c: float,
    Qw: float,
    rw: float,
    t: float,
    grid_m: float,
    ngr: int,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Build a TIMFLOW model and return the head-lowering grid.

    Parameters
    ----------
    kaq, H, c, Qw, rw, t, grid_m, ngr
        See :func:`run_timflow` for descriptions.

    Returns
    -------
    lowering : ndarray, shape (ngr, ngr)
        Head lowering [m] relative to undisturbed head at each grid point.
    x_grid : ndarray, shape (ngr,)
        Local x-coordinates [m] of grid columns (origin at well).
    y_grid : ndarray, shape (ngr,)
        Local y-coordinates [m] of grid rows (origin at well).
    """
    if t > 0:
        return _run_transient(kaq, H, c, Qw, rw, t, grid_m, ngr)
    return _run_steady(kaq, H, c, Qw, rw, grid_m, ngr)


def _run_steady(
    kaq: float,
    H: float,
    c: float,
    Qw: float,
    rw: float,
    grid_m: float,
    ngr: int,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Run steady-state TIMFLOW model."""
    import timflow.steady as tfs

    x_grid = np.linspace(-grid_m, grid_m, ngr)
    y_grid = np.linspace(-grid_m, grid_m, ngr)

    if c > 0:
        # Two-layer: confined aquifer with leaky layer above
        ml = tfs.ModelMaq(
            kaq=[kaq],
            z=[H, 0.0],
            c=[c],
            npor=[0.3],
        )
    else:
        # Single unconfined/confined aquifer
        ml = tfs.ModelMaq(
            kaq=[kaq],
            z=[H, 0.0],
            c=[],
            npor=[0.3],
        )

    # Place pumping well at origin
    tfs.Well(ml, xw=0.0, yw=0.0, Qw=Qw, rw=rw, layers=0)
    ml.solve(silent=True)

    # Compute head on grid – headgrid returns shape (nlayers, nrow, ncol).
    head = ml.headgrid(x_grid, y_grid, layers=0)
    # Reference head at a far-field corner point (scalar for steady models).
    h_ref = float(ml.head(x_grid[-1], y_grid[-1], layers=0))
    lowering = h_ref - head[0]
    lowering = np.clip(lowering, 0, None)
    return lowering, x_grid, y_grid


def _run_transient(
    kaq: float,
    H: float,
    c: float,
    Qw: float,
    rw: float,
    t: float,
    grid_m: float,
    ngr: int,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Run transient TIMFLOW model at time *t*."""
    import timflow.transient as tft

    x_grid = np.linspace(-grid_m, grid_m, ngr)
    y_grid = np.linspace(-grid_m, grid_m, ngr)

    # Storativity: use 1e-4 for confined, 0.1 for unconfined
    Ss = 1e-4 if c > 0 else 0.1

    if c > 0:
        ml = tft.ModelMaq(
            kaq=[kaq],
            z=[H, 0.0],
            c=[c],
            Saq=[Ss * H],
            tmin=1e-3,
            tmax=t * 2,
        )
    else:
        ml = tft.ModelMaq(
            kaq=[kaq],
            z=[H, 0.0],
            c=[],
            Saq=[Ss * H],
            tmin=1e-3,
            tmax=t * 2,
        )

    # Transient Well uses tsandQ [(start_time, discharge), ...]
    tft.Well(ml, xw=0.0, yw=0.0, tsandQ=[(0, Qw)], rw=rw, layers=0)
    ml.solve(silent=True)

    # headgrid returns shape (nlayers, ntimes, nrow, ncol);
    # head returns shape (nlayers, ntimes).
    head = ml.headgrid(x_grid, y_grid, t, layers=0)  # (1, 1, ngr, ngr)
    h_ref = float(ml.head(x_grid[-1], y_grid[-1], t, layers=0)[0, 0])
    lowering = h_ref - head[0, 0]
    lowering = np.clip(lowering, 0, None)
    return lowering, x_grid, y_grid


def _extract_contour_geojson(
    lowering: np.ndarray,
    x_grid: np.ndarray,
    y_grid: np.ndarray,
    lat: float,
    lon: float,
    levels: list[float],
) -> list[dict]:
    """Trace contour lines and return a list of GeoJSON Feature dicts.

    Uses matplotlib's contour tracer (which does not require a display).
    """
    import matplotlib

    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    # Scale factors: degrees per metre at this latitude
    deg_per_m_lat = _DEG_PER_M_LAT
    deg_per_m_lon = _DEG_PER_M_LAT / math.cos(math.radians(lat))

    XX, YY = np.meshgrid(x_grid, y_grid)
    fig, ax = plt.subplots()
    cs = ax.contour(XX, YY, lowering, levels=sorted(levels))

    features = []
    for i, level in enumerate(cs.levels):
        paths = cs.collections[i].get_paths() if hasattr(cs, "collections") else []
        # matplotlib >= 3.8 uses cs.get_paths()
        if not paths:
            try:
                paths = [p for seg in cs.allsegs[i] for p in [_seg_to_path(seg)]]
            except Exception:
                paths = []

        multiline_coords: list[list[list[float]]] = []
        for path in paths:
            vertices = path.vertices if hasattr(path, "vertices") else path
            if len(vertices) < 2:
                continue
            line: list[list[float]] = []
            for xm, ym in vertices:
                wgs_lon = lon + xm * deg_per_m_lon
                wgs_lat = lat + ym * deg_per_m_lat
                line.append([round(wgs_lon, 7), round(wgs_lat, 7)])
            if len(line) >= 2:
                multiline_coords.append(line)

        if multiline_coords:
            features.append(
                {
                    "type": "Feature",
                    "geometry": {
                        "type": "MultiLineString",
                        "coordinates": multiline_coords,
                    },
                    "properties": {"lowering": round(float(level), 4)},
                }
            )

    plt.close(fig)
    return features


def _seg_to_path(seg: np.ndarray):
    """Wrap a raw numpy segment array in a simple object with .vertices."""

    class _P:
        def __init__(self, v):
            self.vertices = v

    return _P(seg)


def _parse_parquet_to_json(file_path: str) -> str:
    """Parse a GeoParquet file and return its records as a JSON array.

    Called from Kotlin via Chaquopy in ``GeoParquetRepository``.

    Parameters
    ----------
    file_path : str
        Absolute path to the GeoParquet file on the Android device.

    Returns
    -------
    str
        JSON array of record objects, each containing at least the fields
        required by :func:`run_timflow` plus ``id``, ``lat``, ``lon``.

    Raises
    ------
    FileNotFoundError
        If *file_path* does not exist.
    ImportError
        If ``pyarrow`` is not available in the embedded Python runtime.
    """
    import os

    if not os.path.exists(file_path):
        raise FileNotFoundError(f"GeoParquet file not found: {file_path}")

    try:
        import pyarrow.parquet as pq
    except ImportError as exc:
        raise ImportError(
            "pyarrow is required to read GeoParquet files. "
            "Ensure it is listed in the Chaquopy pip block."
        ) from exc

    table = pq.read_table(file_path)
    df = table.to_pandas()

    # Drop geometry column if present (GeoParquet stores geometry separately)
    if "geometry" in df.columns:
        df = df.drop(columns=["geometry"])

    # Ensure required columns exist with sensible defaults
    for col, default in [
        ("c", 0.0),
        ("t", 0.0),
        ("rw", 0.1),
        ("grid_m", 2000.0),
        ("ngr", 50),
        ("contour_levels", "0.1,0.5,1,2,5"),
    ]:
        if col not in df.columns:
            df[col] = default

    # The 'id' column may be stored as an integer index
    if "id" not in df.columns:
        df["id"] = df.index.astype(str)
    else:
        df["id"] = df["id"].astype(str)

    return df.to_json(orient="records")
