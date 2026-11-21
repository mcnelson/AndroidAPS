# McNelson's AndroidAPS 3.4 Tweaks

This is my fork of AndroidAPS containing a squash of my current modifications of `3.4`. The primary feature is **QuickBolus**, an Android activity that is a streamlined treatment management UI.

## Brief Summary of Features
### QuickBolus on wear + app
Streamlined bolus UI primarily for use on wear but also on phone. Launcher activity from wear (standalone syringe icon in app drawer). Replaces Treatment dialog on the phone (launch from Treatment button).

### BR+TT Complication
Complication showing basal rate and either profile target or temporary target. If TT is active, replaces basal rate and shows how many minutes are remaining.

### Glucose Dot Complication
A watch complication showing a dot that reflects current glucose.

### Pump+Sensor Expiration Hrs Complication
Complication showing 3 numbers: a combination of (1) hours left on Libre 3 sensor, (2) hours left on Omnipod, and (3) insulin units remaining on the pod visible only if <50.

### Loop recalculation throttling
Reduce loop recalculation frequency from 1min to 4min for significant improvement in battery life.

### Standalone zoomable graph on wear
Reproduction of the main AAPS graph. Pinch-zoomable. Includes reproduction of the main AAPS header showing IOB, COB, BR, and AS as well. This is WIP/scrappy feature.
