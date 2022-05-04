function test {
    $a = 2
}
function test2 {
    param (
        [string] $value,
        [string] $test,
        [string] $test2
    )
    Write-Host 555
}

function test3($testValue1, $testValue2) {
    Write-Host $testValue1
    test2 "hi" "some string" "more string"
    test2 -value "im value" -test2 "im test2" -test "im tetst"
}