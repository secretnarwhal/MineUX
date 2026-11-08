<#
  A plain window showing one picture, for Screens' showcase shots: `screens add <window id>` puts
  it in the world instead of a real monitor, so no personal desktop ever lands in a picture.

    powershell -File tools/showcase/source_window.ps1 <image.png> [title] [x y w h]
#>
param([string]$Image, [string]$Title = 'MineUX showcase', [int]$X = 40, [int]$Y = 40, [int]$W = 1280, [int]$H = 720)
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
$form = New-Object System.Windows.Forms.Form
$form.Text = $Title
$form.StartPosition = 'Manual'
$form.Location = New-Object System.Drawing.Point($X, $Y)
$form.ClientSize = New-Object System.Drawing.Size($W, $H)
$form.FormBorderStyle = 'None'
$form.BackColor = [System.Drawing.Color]::Black
$box = New-Object System.Windows.Forms.PictureBox
$box.Dock = 'Fill'
$box.SizeMode = 'Zoom'
$box.Image = [System.Drawing.Image]::FromFile([IO.Path]::GetFullPath($Image))
$form.Controls.Add($box)
[void]$form.ShowDialog()
