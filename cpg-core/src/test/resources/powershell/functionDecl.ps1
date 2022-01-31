function testFunc {
    param (
        $test, $param2
    )
    $test = 5
    #Write-Host($a)
}
$hi = 4
testFunc -test $hi -param2 10