# Exporta el PUDS y actualiza el índice con Microsoft Word instalado.
$ErrorActionPreference='Stop'
$taskRoot=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$docPath=Join-Path $taskRoot 'entrega\PUDS_INF513_P1_Grupo09SA.docx'
$pdfPath=Join-Path $taskRoot 'entrega\PUDS_INF513_P1_Grupo09SA.pdf'
$word=$null;$document=$null
try {
    $word=New-Object -ComObject Word.Application
    $word.Visible=$false;$word.DisplayAlerts=0
    $document=$word.Documents.Open($docPath)
    $document.UpdateStylesOnOpen=$false
    foreach($taskStyle in @(-1,-2,-3,-4,-5,-63)){
        $document.Styles.Item($taskStyle).Font.Name='Times New Roman'
    }
    $document.Styles.Item(-63).ParagraphFormat.Borders.Enable=0
    $null=$document.Fields.Update()
    foreach($toc in $document.TablesOfContents){
        $toc.Update()
        $toc.Range.Font.Name='Times New Roman'
        $toc.Range.Font.Size=12
        $toc.Range.ParagraphFormat.LineSpacingRule=4
        $toc.Range.ParagraphFormat.LineSpacing=18
        $toc.Range.ParagraphFormat.SpaceBefore=0
        $toc.Range.ParagraphFormat.SpaceAfter=0
    }
    $document.Repaginate()
    foreach($toc in $document.TablesOfContents){$toc.UpdatePageNumbers()}
    $document.Save();$document.ExportAsFixedFormat($pdfPath,17)
    [pscustomobject]@{Pages=$document.ComputeStatistics(2);PDF=$pdfPath} | ConvertTo-Json
} finally {
    if($document){$document.Close(0);$null=[Runtime.InteropServices.Marshal]::ReleaseComObject($document)}
    if($word){$word.Quit();$null=[Runtime.InteropServices.Marshal]::ReleaseComObject($word)}
}
