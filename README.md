<img src="./docs/_static/timflow_logo.jpeg" width="300">

![PyPI - Version](https://img.shields.io/pypi/v/timflow)
[![General](https://github.com/timflow-org/timflow/actions/workflows/ci-general.yml/badge.svg)](https://github.com/timflow-org/timflow/actions/workflows/ci-general.yml)
[![Steady](https://github.com/timflow-org/timflow/actions/workflows/ci-steady.yml/badge.svg)](https://github.com/timflow-org/timflow/actions/workflows/ci-steady.yml)
[![Transient](https://github.com/timflow-org/timflow/actions/workflows/ci-transient.yml/badge.svg)](https://github.com/timflow-org/timflow/actions/workflows/ci-transient.yml)
[![Documentation Status](https://readthedocs.org/projects/timflow/badge/?version=latest)](https://timflow.readthedocs.io/en/latest/?badge=latest)
[![Ruff](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/astral-sh/ruff/main/assets/badge/v2.json)](https://github.com/astral-sh/ruff)

# timflow, a multi-layer analytic element model

`timflow` is a Python package for the modeling of multi-layer groundwater flow with analytic
elements. The package is split into two main submodules: `timflow.steady` for steady-state flow
and `timflow.transient` for modeling transient flow. Both modules may be applied to an
arbitrary number of aquifers and leaky layers. The head, flow, and leakage between
aquifers may be computed semi-analytically at any point in space and time.

The design
of `timflow` is object-oriented and has been kept simple and flexible.
New analytic elements may be added to the code without making any changes in the
existing part of the code. `timflow` is coded in Python and uses `numba` to speed up
evaluation of the line elements and inverse Laplace transforms.
The `transient` submodule is based on the Laplace-transform analytic element
method. The solution is computed analytically in the Laplace domain and converted back
to the time domain numerically usig the algorithm of De Hoog, Stokes, and Knight.

## Installation

`timflow` requires Python >= 3.11 and can be installed from PyPI:

`pip install timflow`

To install all optional dependencies (for running tests and building docs):

`pip install timflow[dev]`

## Documentation

The documentation is hosted on [readthedocs](https://timflow.readthedocs.io).

## History

`timflow` combines the old packages [`TimML`](https://github.com/mbakker7/timml)
and [`TTim`](https://github.com/mbakker7/ttim). Git history is maintained within this new package, but the old repositories are still available for searching through old issues, pull requests, and other information.

## Citation

Some of the papers that you may want to cite when using `timflow` are:

* Steady-state flow:
  * Bakker, M., and O.D.L. Strack. 2003. Analytic Elements for Multiaquifer Flow.
  Journal of Hydrology, 271(1-4), 119-129. [https://doi.org/10.1016/S0022-1694(02)00319-0](https://doi.org/10.1016/S0022-1694(02)00319-0)
* Transient flow:
  * M. Bakker. 2013. Semi-analytic modeling of transient multi-layer flow with TTim.
  Hydrogeology Journal, 21: 935-943. [https://doi.org/10.1007/s10040-013-0975-2](https://doi.org/10.1007/s10040-013-0975-2)
  * M .Bakker. 2013. Analytic modeling of transient multi-layer flow. In: Advances in
  Hydrogeology, edited by P Mishra and K Kuhlman, Springer, Heidelberg, 95-114.

---

## HydroApp – Android Field Tool

The `android/` directory contains a companion **Android 15 (API 35)** application
that brings real-time TIMFLOW groundwater-lowering calculations to the field.

### Features

| Feature | Technology |
|---|---|
| GPS location with accuracy indicator | Android Fused Location Provider |
| Nearest-neighbour hydro-parameter lookup | Haversine search + Room cache |
| GeoParquet data loading | PyArrow via Chaquopy |
| TIMFLOW groundwater model | Python 3.11 via Chaquopy |
| Head-lowering contour visualisation | GeoJSON + OSMDroid (OpenStreetMap) |
| Offline map tiles | OSMDroid tile cache |
| Dependency injection | Hilt |
| Local parameter cache | Room database |

### Architecture

```
android/
├── app/
│   ├── build.gradle.kts              # App-level build (AGP 8.7, Chaquopy 16)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/timflow/hydroapp/
│       │   ├── HydroApplication.kt   # @HiltAndroidApp entry point
│       │   ├── MainActivity.kt       # Single-activity Compose host
│       │   ├── di/AppModule.kt       # Hilt bindings (Room, DAO)
│       │   ├── location/
│       │   │   ├── LocationManager.kt  # Fused GPS flow
│       │   │   └── LocationState.kt    # Sealed state class
│       │   ├── data/
│       │   │   ├── HydroParameters.kt  # Room entity + serializable model
│       │   │   ├── HydroDatabase.kt    # Room database
│       │   │   ├── HydroRecordDao.kt   # Room DAO
│       │   │   └── GeoParquetRepository.kt  # Parquet load + nearest search
│       │   ├── python/
│       │   │   └── TimflowBridge.kt  # Chaquopy → Python bridge
│       │   └── ui/
│       │       ├── MapViewModel.kt   # Hilt ViewModel
│       │       ├── MapScreen.kt      # Compose screen + OSMDroid
│       │       └── theme/Theme.kt    # Material 3 aquifer-blue theme
│       └── python/
│           └── timflow_mobile.py     # Python TIMFLOW wrapper (tested)
├── build.gradle.kts                  # Root project build
├── settings.gradle.kts
├── gradle.properties
└── gradle/
    ├── libs.versions.toml            # Version catalog
    └── wrapper/gradle-wrapper.properties
```

### Prerequisites

| Tool | Version |
|---|---|
| Android Studio | Ladybug (2024.2) or later |
| Android SDK | API 35 (Android 15) |
| JDK | 17 |
| Python (host, for Chaquopy build) | 3.11 |

### Build instructions

```bash
# 1. Clone and enter the android subdirectory
git clone https://github.com/Died1808/timflow
cd timflow/android

# 2. (Optional) point to your Python 3.11 installation so Chaquopy
#    can resolve pip packages at build time
echo "python.version=3.11" >> local.properties

# 3. Build a debug APK
./gradlew assembleDebug

# 4. Install on a connected device / emulator
./gradlew installDebug
```

The first build downloads all Python wheels and assembles them into the APK;
this may take several minutes depending on network speed.

### Preparing hydrogeological data

The app reads a **GeoParquet** file containing site-specific aquifer parameters.
Each record must include the following columns:

| Column | Type | Description |
|---|---|---|
| `id` | str | Unique record identifier |
| `lat` | float | Latitude (WGS-84, decimal degrees) |
| `lon` | float | Longitude (WGS-84, decimal degrees) |
| `kaq` | float | Hydraulic conductivity [m/d] |
| `H` | float | Saturated aquifer thickness [m] |
| `Qw` | float | Pumping rate [m³/d] |
| `rw` | float | Well radius [m] (default: 0.1) |
| `c` | float | Aquitard resistance [d] (0 = single layer) |
| `t` | float | Simulation time [d] (0 = steady-state) |
| `grid_m` | float | Grid half-width [m] (default: 2000) |
| `ngr` | int | Grid resolution (default: 50) |
| `contour_levels` | str | Comma-separated lowering levels [m] |

Place the file at the path shown in the app's status card
(`<internal_storage>/hydro_data/hydro_params.parquet`), then tap
**Compute Lowering Contours**.

### Python wrapper (standalone)

`timflow_mobile.py` can also be used as a standalone Python module without
the Android app:

```python
import pandas as pd
from android.app.src.main.python.timflow_mobile import run_timflow

df = pd.DataFrame([{
    "lat": 52.0, "lon": 4.0,
    "kaq": 10.0, "H": 20.0, "c": 0.0,
    "Qw": 1000.0, "rw": 0.1,
    "t": 0.0, "grid_m": 2000.0, "ngr": 50,
    "contour_levels": "0.1,0.5,1,2,5",
}])
geojson_str = run_timflow(df)
```

Tests for the wrapper are in `tests/test_timflow_mobile.py` and run with the
standard `pytest` suite.

