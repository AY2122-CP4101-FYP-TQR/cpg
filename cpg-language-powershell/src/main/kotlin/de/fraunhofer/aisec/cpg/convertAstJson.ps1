function stripIllegalText($text) {
  if ($text -match (".*`".*")) {
    $text = $text -replace "`"","`'"
  }
  if ($text -match (".*\n.*")) {
    $text = $text -replace "\n",""
  }
  if ($text -match (".*\r.*")) {
    $text = $text -replace "\r",""
  }
  return $text
}

function printAst($id, $Indent = 0)
{
  # use this as indent per level:
  $space = ' ' * $indent
  #$hierachy[$id] is an array that contains its children's ID
  $childCounter = $hierarchy[$id].Count
  $output = ""

  $hierarchy[$id] | ForEach-Object {
    $newid = $_.GetHashCode()

    $value = stripIllegalText -text $_
    $output += "{0} {{`"type`": `"{1}`", `"code`": `"{2}`"" -f $space, $_.GetType().Name, $value
    if ($null -ne $_.name) {
      $output += ",`"name`": `"{0}`"" -f $_.name
    }
    if ($null -ne $_.StaticType) {
      $output += ",`"codeType`": `"{0}`"" -f $_.StaticType.Name
    }
    if ($null -ne $_.Operator) {
      $op = $_.Operator.tostring()
      if ($op[0] -eq "I") {
        $op = $op -replace "I","-"
      }
      $output += ",`"operator`": `"{0}`"" -f $op
    }
    if ($null -ne $_.TokenKind) {
      $output += ",`"unaryType`": `"{0}`"" -f $_.TokenKind
    }

    if ($_.GetType().Name -like "ForStatementAst") {
      $output += ", `"for`": {{`"init`": `"{0}`", `"iterator`": `"{1}`", `"condition`": `"{2}`", `"body`": `"{3}`" }} " -f ($null -ne $_.Initializer), ($null -ne $_.Iterator), ($null -ne $_.Condition), ($null -ne $_.Body)
      $output += printAst -id $newid -indent ($indent + 1)
      $output += "]"
    }

    if ($_.GetType().Name -like "FunctionDefinitionAst") {
      $output += ", `"function`": {`"param`": ["
      $count = 0
      if ($null -ne $_.Parameters) {
        $len = $_.parameters.Count
        foreach ($param in $_.Parameters) {
          if ($count -ne 0 -and $count -ne $len) {
            $output += ", "
          }
          $output += "`"{0}`"" -f $param.name
          $count += 1
        }
        $output += "], `"type`": ["
        foreach ($param in $_.Parameters.StaticType) {
          if ($count -ne 0 -and $count -ne $len) {
            $output += ", "
          }
          $output += "`"{0}`"" -f $param.name
          $count += 1
        }
      } else {
        $len = $_.Body.ParamBlock.Parameters.Count
        foreach ($param in $_.Body.ParamBlock.Parameters) {
          if ($count -ne 0 -and $count -ne $len) {
            $output += ", "
          }
          $output += "`"{0}`"" -f $param.name
          $count += 1
        }
        $output += "], `"type`": ["
        foreach ($param in $_.Body.ParamBlock.Parameters.StaticType) {
          if ($count -ne 0 -and $count -ne $len) {
            $output += ", "
          }
          $output += "`"{0}`"" -f $param.name
          $count += 1
        }
      }
      $value = stripIllegalText -text $_.Body.EndBlock.Extent.Text
      $output += "], `"body`": `"{0}`" }}" -f ($value)  #Can be extended to add other Blocks
    }

    $output += ", `"location`": {{`"file`": `"{0}`", `"startLine`": `"{1}`", `"endLine`": `"{2}`", `"startCol`": `"{3}`", `"endCol`": `"{4}`" }}" -f $program, $_.Extent.StartLineNumber, $_.Extent.EndLineNumber, $_.Extent.StartColumnNumber, $_.Extent.EndColumnNumber
    #can add more if required
    #"{0} Type:{1} Code:{2} Children:{3} `n" -f $space, $_.getType().Name, $_.Extent.Text, $childCounter
    if ($hierarchy.ContainsKey($newid)) {
      $output += ", `"children`": [ `n"
      $output += printAst -id $newid -indent ($indent + 1)
      $output += "]"
    }
    if ($childCounter -gt 1) {
      $output += "},`n"
      $childCounter -= 1
    }
  }
  $output += "}"
  return $output
}

$program = $args[0]
$code = Get-Content -Raw -Path $program

#$counter = 0
#$ast = [System.Management.Automation.Language.Parser]::ParseFile($program, [ref]$tokens, [ref]$errors)

#Handle the JSON parsing afterwards
$hierarchy = @{}
$code = [Scriptblock]::Create($code)

$code.Ast.FindAll( { $true }, $true) |
        ForEach-Object {
          if ($_.Parent -ne $null) {
            # take unique object hash as key
            $id = $_.Parent.GetHashCode()
            if ($hierarchy.ContainsKey($id) -eq $false)
            {
              $hierarchy[$id] = [System.Collections.ArrayList]@()
            }
            $null = $hierarchy[$id].Add($_)
            # add ast object to parent
          }
        }

#$hierarchy
# start visualization with ast root object:
printAst -id $code.Ast.GetHashCode() #| ConvertTo-Json | ConvertFrom-Json