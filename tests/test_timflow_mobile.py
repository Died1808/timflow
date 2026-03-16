"""Tests for the TIMFLOW mobile wrapper (android/app/src/main/python/timflow_mobile.py).

These tests run with the standard pytest infrastructure and validate the
Python functions independently of the Android/Chaquopy runtime.
"""

import importlib.util
import json
import math
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import pytest

# ---------------------------------------------------------------------------
# Import the mobile module directly (not installed, just on the path).
# ---------------------------------------------------------------------------

MODULE_PATH = (
    Path(__file__).parent.parent
    / "android"
    / "app"
    / "src"
    / "main"
    / "python"
    / "timflow_mobile.py"
)

spec = importlib.util.spec_from_file_location("timflow_mobile", MODULE_PATH)
timflow_mobile = importlib.util.module_from_spec(spec)
spec.loader.exec_module(timflow_mobile)

run_timflow = timflow_mobile.run_timflow
run_timflow_from_json = timflow_mobile.run_timflow_from_json
nearest_record = timflow_mobile.nearest_record
haversine = timflow_mobile._haversine


# ---------------------------------------------------------------------------
# Haversine helper
# ---------------------------------------------------------------------------


def test_haversine_same_point():
    """Distance between identical points must be zero."""
    assert haversine(52.0, 4.0, 52.0, 4.0) == pytest.approx(0.0)


def test_haversine_known_distance():
    """Amsterdam → Rotterdam ≈ 57 km (air line)."""
    dist = haversine(52.3676, 4.9041, 51.9225, 4.4792)
    assert 55_000 < dist < 60_000, f"Unexpected distance: {dist:.0f} m"


# ---------------------------------------------------------------------------
# nearest_record
# ---------------------------------------------------------------------------


def _make_records_json():
    records = [
        {"id": "A", "lat": 52.0, "lon": 4.0, "kaq": 10, "H": 20, "Qw": 1000, "rw": 0.1},
        {"id": "B", "lat": 51.0, "lon": 3.5, "kaq": 5, "H": 15, "Qw": 500, "rw": 0.1},
        {"id": "C", "lat": 53.0, "lon": 4.5, "kaq": 20, "H": 25, "Qw": 2000, "rw": 0.1},
    ]
    return json.dumps(records)


def test_nearest_record_returns_closest():
    records_json = _make_records_json()
    result = json.loads(nearest_record(52.01, 4.01, records_json))
    assert result["id"] == "A"


def test_nearest_record_empty_raises():
    with pytest.raises(ValueError, match="at least one record"):
        nearest_record(52.0, 4.0, "[]")


# ---------------------------------------------------------------------------
# run_timflow – validation
# ---------------------------------------------------------------------------


def _minimal_df(**kwargs):
    """Return a minimal valid single-row DataFrame."""
    base = {
        "lat": 52.0,
        "lon": 4.0,
        "kaq": 10.0,
        "H": 20.0,
        "c": 0.0,
        "Qw": 1000.0,
        "rw": 0.1,
        "t": 0.0,
        "grid_m": 500.0,
        "ngr": 20,
        "contour_levels": "0.5,1,2",
    }
    base.update(kwargs)
    return pd.DataFrame([base])


def test_run_timflow_missing_column_raises():
    df = _minimal_df()
    df = df.drop(columns=["kaq"])
    with pytest.raises(ValueError, match="Missing required columns"):
        run_timflow(df)


def test_run_timflow_empty_df_raises():
    df = _minimal_df()
    df = df.iloc[0:0]  # empty
    with pytest.raises(KeyError):
        run_timflow(df)


def test_run_timflow_invalid_kaq_raises():
    df = _minimal_df(kaq=-5.0)
    with pytest.raises(ValueError, match="kaq must be positive"):
        run_timflow(df)


def test_run_timflow_invalid_H_raises():
    df = _minimal_df(H=0.0)
    with pytest.raises(ValueError, match="H must be positive"):
        run_timflow(df)


def test_run_timflow_returns_geojson():
    """Steady-state run should return valid GeoJSON FeatureCollection."""
    df = _minimal_df()
    result = run_timflow(df)
    fc = json.loads(result)
    assert fc["type"] == "FeatureCollection"
    assert isinstance(fc["features"], list)
    assert len(fc["features"]) >= 1  # at least the well point


def test_run_timflow_has_well_feature():
    df = _minimal_df()
    fc = json.loads(run_timflow(df))
    well_features = [
        f for f in fc["features"]
        if f.get("geometry", {}).get("type") == "Point"
    ]
    assert len(well_features) == 1
    props = well_features[0]["properties"]
    assert props["type"] == "well"
    assert props["Qw"] == pytest.approx(1000.0)


def test_run_timflow_contours_have_lowering_property():
    df = _minimal_df()
    fc = json.loads(run_timflow(df))
    contour_features = [
        f for f in fc["features"]
        if f.get("geometry", {}).get("type") == "MultiLineString"
    ]
    for feat in contour_features:
        assert "lowering" in feat["properties"]
        assert feat["properties"]["lowering"] > 0


def test_run_timflow_coordinates_near_well():
    """Contour coordinates should be in the vicinity of the well location."""
    lat, lon = 52.0, 4.0
    df = _minimal_df(lat=lat, lon=lon)
    fc = json.loads(run_timflow(df))
    for feat in fc["features"]:
        geom = feat["geometry"]
        if geom["type"] == "MultiLineString":
            for line in geom["coordinates"]:
                for coord in line:
                    c_lon, c_lat = coord[0], coord[1]
                    # All contour points should be within ±1° of well
                    assert abs(c_lat - lat) < 1.0, f"Lat out of range: {c_lat}"
                    assert abs(c_lon - lon) < 1.0, f"Lon out of range: {c_lon}"


def test_run_timflow_metadata():
    df = _minimal_df(kaq=15.0, H=30.0, Qw=500.0)
    fc = json.loads(run_timflow(df))
    meta = fc["metadata"]
    assert meta["kaq"] == pytest.approx(15.0)
    assert meta["H"] == pytest.approx(30.0)
    assert meta["Qw"] == pytest.approx(500.0)


# ---------------------------------------------------------------------------
# run_timflow_from_json
# ---------------------------------------------------------------------------


def test_run_timflow_from_json_matches_dataframe():
    """JSON and DataFrame interfaces should produce identical GeoJSON."""
    params = {
        "lat": 52.0,
        "lon": 4.0,
        "kaq": 10.0,
        "H": 20.0,
        "c": 0.0,
        "Qw": 1000.0,
        "rw": 0.1,
        "t": 0.0,
        "grid_m": 500.0,
        "ngr": 20,
        "contour_levels": "0.5,1,2",
    }
    result_json = run_timflow_from_json(json.dumps(params))
    result_df = run_timflow(pd.DataFrame([params]))

    fc_json = json.loads(result_json)
    fc_df = json.loads(result_df)

    # Both should have the same number of features
    assert len(fc_json["features"]) == len(fc_df["features"])


def test_run_timflow_from_json_invalid_json_raises():
    with pytest.raises(Exception):
        run_timflow_from_json("not-valid-json")


# ---------------------------------------------------------------------------
# Leaky / two-layer model
# ---------------------------------------------------------------------------


def test_run_timflow_with_leaky_layer():
    """A non-zero resistance c should still produce valid GeoJSON."""
    df = _minimal_df(c=500.0)
    result = run_timflow(df)
    fc = json.loads(result)
    assert fc["type"] == "FeatureCollection"
