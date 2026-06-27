# Issue #367: refactor(ios): arrest IosPeripheral regrowth (286->300) back below 250 lines
- Severity: low
- Impact: low
- Approach: Decompose IosPeripheral.kt by extracting peripheral operation handlers into separate modules. Consider extracting: (1) GATT operations (read/write/notify), (2) service discovery, (3) connection management. Aim for 50-80 lines per extracted module. Verify compileKotlinIos after each extraction.
- Files to touch: IosPeripheral.kt (iosMain), new handler modules in iosMain
- Risks: Refactoring large files risks introducing regressions. Must maintain iOS platform API compatibility. Need thorough test coverage before/during/after refactor.
