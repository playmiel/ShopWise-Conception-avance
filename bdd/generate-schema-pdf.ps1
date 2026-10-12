# Genere "bdd/schema base de donnees.pdf" (avec accent) a partir de bdd/schema.mmd (diagramme Mermaid, aussi affiche dans readme.md).
# Necessite Docker (image officielle mermaid-cli) ; aucune installation locale.
# Usage depuis la racine du projet : powershell -File bdd/generate-schema-pdf.ps1
$bdd = $PSScriptRoot
# Nom construit avec le code du caractere : le script reste ASCII, donc lu correctement par toutes les versions de PowerShell
$pdf = 'schema base de donn' + [char]0xE9 + 'es.pdf'
docker run --rm -v "${bdd}:/data" ghcr.io/mermaid-js/mermaid-cli/mermaid-cli:11.4.2 `
    -i /data/schema.mmd -o "/data/$pdf" --pdfFit -b white
if ($LASTEXITCODE -eq 0) { "PDF genere : $bdd\$pdf" } else { "Echec de la generation (code $LASTEXITCODE)"; exit $LASTEXITCODE }