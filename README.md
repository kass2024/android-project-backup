# Android project backup

Source backup of the projects in `AndroidStudioProjects`, one folder per project.

Left out of this repo:

- Gradle and Android build output (`build/`, `.gradle/`)
- IDE files (`.idea/`)
- `node_modules/` and `.next/`
- `local.properties`
- Signing keys and keystore passwords (`*.jks`, `keystore.properties`, `keys/`)
- Any file 100 MB or larger (GitHub rejects those). Split copies under `_apk_chunks/` are included.
