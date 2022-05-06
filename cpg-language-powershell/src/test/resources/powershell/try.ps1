Try
{
    1/0
    Write-Host "HELLO"
}
Catch [System.Management.Automation.RuntimeException]
{
    Write-Host "An error occurred for RUNTIME"
}
Catch
{
    Write-Host "An error occurred without type"
}
Finally
{
    Write-Host "cleaning up ..."
}