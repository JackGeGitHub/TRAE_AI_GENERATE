# Get all PowerShell commands with numbering and generate Markdown document

# Create output directory
$docPath = "d:\Workspace\Android\Codes\TRAE_AI_GENERATE\CaptureScreen\doc"
if (-not (Test-Path $docPath)) {
    New-Item -ItemType Directory -Path $docPath -Force
}

$outputFile = "$docPath\PowerShellAllCommand.md"

# Write document header
$header = "# PowerShell All Commands Detailed Information

## Generation Time
$(Get-Date -Format "yyyy-MM-dd HH:mm:ss")

## Total Commands
$((Get-Command).Count)

## Command Details

"

$header | Out-File -FilePath $outputFile -Encoding UTF8

# Get all commands and sort them
$commands = Get-Command | Sort-Object CommandType, Name
$commandCount = 0

# Process each command
foreach ($command in $commands) {
    $commandCount++
    
    try {
        # Write command with numbering
        $commandHeader = "### $commandCount. $($command.Name) ($($command.CommandType))

"
        $commandHeader | Out-File -FilePath $outputFile -Append -Encoding UTF8
        
        # Try to get command help
        $help = Get-Help $command.Name -Full -ErrorAction Stop
        
        # Write command syntax
        $syntax = "#### Syntax
```
$($help.Syntax | Out-String)
```

"
        $syntax | Out-File -FilePath $outputFile -Append -Encoding UTF8
        
        # Write command description
        if ($help.Description) {
            $description = "#### Description
$($help.Description | Out-String)

"
            $description | Out-File -FilePath $outputFile -Append -Encoding UTF8
        }
        
        # Write parameter information
        if ($help.Parameters) {
            $parameters = "#### Parameters
```
$($help.Parameters | Out-String)
```

"
            $parameters | Out-File -FilePath $outputFile -Append -Encoding UTF8
        }
        
    } catch {
        # Write error if help cannot be obtained
        $errorMsg = "#### Error
Failed to get command help: $($_.Exception.Message)

"
        $errorMsg | Out-File -FilePath $outputFile -Append -Encoding UTF8
    }
    
    # Write separator
    $separator = "---

"
    $separator | Out-File -FilePath $outputFile -Append -Encoding UTF8
}

Write-Host "PowerShell command information with numbering has been successfully output to: $outputFile"
Write-Host "Total commands: $commandCount"
