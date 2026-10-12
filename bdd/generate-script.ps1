# Regenere bdd/script.sql a partir des migrations Flyway (source de verite du schema).
# Usage : powershell -File bdd/generate-script.ps1   (depuis la racine du projet)
$root = Split-Path -Parent $PSScriptRoot
$migrations = Get-ChildItem "$root/src/main/resources/db/migration" -Filter 'V*__*.sql' |
    Sort-Object { [int]($_.Name -replace '^V(\d+)__.*', '$1') }
$out = @(
    '-- ShopWise : script complet de la base de donnees (schema + donnees initiales).',
    '-- GENERE a partir des migrations Flyway de src/main/resources/db/migration : ne pas modifier a la main,',
    '-- relancer bdd/generate-script.ps1. Le test DatabaseScriptTest verifie la concordance.',
    '-- SQL standard compatible H2 2.x et PostgreSQL.',
    ''
)
foreach ($m in $migrations) {
    $out += "-- ============================================================"
    $out += "-- $($m.Name)"
    $out += "-- ============================================================"
    $out += (Get-Content $m.FullName -Raw).Replace("`r`n", "`n").TrimEnd()
    $out += ''
}
[IO.File]::WriteAllText("$root/bdd/script.sql", ($out -join "`n"), (New-Object Text.UTF8Encoding $false))
"bdd/script.sql regenere ($($migrations.Count) migrations)"