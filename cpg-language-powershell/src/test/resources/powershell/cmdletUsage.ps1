$sb = {Get-Process powershell; Get-Service W32Time}
Invoke-Command -ScriptBlock $sb