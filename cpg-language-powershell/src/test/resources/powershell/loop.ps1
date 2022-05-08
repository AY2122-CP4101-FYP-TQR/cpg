for ($i = 0; $i -lt 5; $i++) {
    $a = 5
}

$i = 0
while ($i -lt 5) {
    $a = 5
    $i++
}

$i = 0
do {
    $a = 5
    $i++
} while($i -lt 5)

$i = 0
do {
    $a = 5
    $i++
} until($i -gt 5)

$arr = @(50, 20, 42)
foreach($a in $arr) {
    Write-Host $a
    $b = $a + 2
}
