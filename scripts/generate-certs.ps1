param(
    [string]$Password = "changeit",
    [string]$OutputDirectory = "",
    [string]$GatewayPublicSan = ""
)
$ErrorActionPreference = "Stop"
$certDir = if ($OutputDirectory) {
    [System.IO.Path]::GetFullPath($OutputDirectory)
} else {
    [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.certs"))
}
$keytool = "C:\Program Files\Java\jdk-21\bin\keytool.exe"
$openssl = "C:\Program Files\Git\usr\bin\openssl.exe"
if (-not (Test-Path -LiteralPath $keytool)) { throw "JDK 21 keytool was not found at $keytool" }
if (-not (Test-Path -LiteralPath $openssl)) { throw "OpenSSL was not found at $openssl" }
New-Item -ItemType Directory -Force -Path $certDir | Out-Null

function Export-ClientPem([string]$Name) {
    & $openssl pkcs12 -in "$certDir\$Name.p12" -passin "pass:$Password" -clcerts -nokeys -out "$certDir\$Name.crt.pem"
    if ($LASTEXITCODE -ne 0) { throw "OpenSSL certificate export failed for $Name." }
    & $openssl pkcs12 -in "$certDir\$Name.p12" -passin "pass:$Password" -nocerts -nodes -out "$certDir\$Name.key.pem"
    if ($LASTEXITCODE -ne 0) { throw "OpenSSL private-key export failed for $Name." }
}

if ((Test-Path "$certDir\gateway.p12") -and (Test-Path "$certDir\partner-client.p12") -and (Test-Path "$certDir\dashboard-client.p12") -and (Test-Path "$certDir\truststore.p12")) {
    Export-ClientPem 'partner-client'
    Export-ClientPem 'dashboard-client'
    Write-Host "Certificates already exist in $certDir"
    exit 0
}

function Run-Keytool([string[]]$Arguments) {
    & $keytool @Arguments
    if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }
}
function Issue-Certificate([string]$Name, [string]$Subject, [string]$ExtendedUsage, [string]$San) {
    Run-Keytool @('-genkeypair','-alias',$Name,'-keyalg','RSA','-keysize','2048','-dname',$Subject,'-validity','825','-keystore',"$certDir\$Name.p12",'-storetype','PKCS12','-storepass',$Password,'-keypass',$Password,'-ext',"SAN=$San",'-noprompt')
    Run-Keytool @('-certreq','-alias',$Name,'-keystore',"$certDir\$Name.p12",'-storepass',$Password,'-file',"$certDir\$Name.csr",'-ext',"SAN=$San")
    Run-Keytool @('-gencert','-alias','ca','-keystore',"$certDir\ca.p12",'-storepass',$Password,'-infile',"$certDir\$Name.csr",'-outfile',"$certDir\$Name.crt",'-rfc','-validity','825','-ext','KU=digitalSignature,keyEncipherment','-ext',"EKU=$ExtendedUsage",'-ext',"SAN=$San")
    Run-Keytool @('-importcert','-alias','ca','-keystore',"$certDir\$Name.p12",'-storepass',$Password,'-file',"$certDir\ca.crt",'-noprompt')
    Run-Keytool @('-importcert','-alias',$Name,'-keystore',"$certDir\$Name.p12",'-storepass',$Password,'-file',"$certDir\$Name.crt",'-noprompt')
}

Run-Keytool @('-genkeypair','-alias','ca','-keyalg','RSA','-keysize','2048','-dname','CN=EDIFlow Development CA,O=EDIFlow','-ext','bc=ca:true','-validity','3650','-keystore',"$certDir\ca.p12",'-storetype','PKCS12','-storepass',$Password,'-keypass',$Password,'-noprompt')
Run-Keytool @('-exportcert','-alias','ca','-keystore',"$certDir\ca.p12",'-storepass',$Password,'-rfc','-file',"$certDir\ca.crt")
$gatewaySan = 'dns:gateway,dns:localhost,ip:127.0.0.1'
if ($GatewayPublicSan) { $gatewaySan = "$gatewaySan,$GatewayPublicSan" }
Issue-Certificate 'gateway' 'CN=gateway,O=EDIFlow' 'serverAuth' $gatewaySan
Issue-Certificate 'partner-client' 'CN=partner-client,OU=B2B Partners,O=EDIFlow' 'clientAuth' 'dns:partner-client'
Issue-Certificate 'dashboard-client' 'CN=dashboard-client,OU=Operations,O=EDIFlow' 'clientAuth' 'dns:dashboard-client'
Run-Keytool @('-importcert','-alias','ediflow-ca','-keystore',"$certDir\truststore.p12",'-storetype','PKCS12','-storepass',$Password,'-file',"$certDir\ca.crt",'-noprompt')

Export-ClientPem 'dashboard-client'
Export-ClientPem 'partner-client'
Write-Host "Certificates generated in $certDir"
