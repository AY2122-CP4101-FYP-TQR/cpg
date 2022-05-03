function test {
    $a = 2
}
function testFunc {
    param (
        $value,
        $test,
        $test2
    )
    Write-Host $value, $test, $test2
}

#$no = "asd"
#$why = "why"

#testFunc ("hi", $no, $why)
#testFunc ("hi"), $no, $why
#testFunc ("test", $no), $why
#testFunc "special" -test2 $why -test $no
#testFunc