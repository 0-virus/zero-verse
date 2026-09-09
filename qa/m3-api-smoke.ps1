<#!
.SYNOPSIS
    M3 category HTTP smoke against a local loopback server.

.DESCRIPTION
    Creates two synthetic accounts, exercises the existing auth/initial-setup DTOs,
    and checks the approved M3 category contract over HTTP. Tokens and passwords stay
    in memory. This intentionally does not verify SQL post moves, post counts, or the
    101-root pagination boundary; those require backend integration evidence.
#>
[CmdletBinding()]
param(
    [string]$BaseUri = 'http://127.0.0.1:8080'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$HttpClient = $null
$HttpHandler = $null

function Assert-LoopbackBaseUri {
    param([string]$Value)

    try {
        $uri = [Uri]$Value
    } catch {
        throw 'BaseUri must be a valid URI.'
    }

    $hostName = $uri.Host.ToLowerInvariant()
    if ($uri.Scheme -notin @('http', 'https') -or
        $hostName -notin @('localhost', '127.0.0.1') -or
        $uri.UserInfo -or $uri.Query -or $uri.Fragment -or
        $uri.AbsolutePath -notin @('', '/')) {
        throw 'BaseUri must be an http(s) localhost or 127.0.0.1 loopback URI without a path or query.'
    }

    return $uri.GetLeftPart([UriPartial]::Authority).TrimEnd('/')
}

function Get-JsonProperty {
    param(
        [AllowNull()][object]$Object,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Label
    )

    if ($null -eq $Object) {
        throw ("{0}: JSON object is null." -f $Label)
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        throw ("{0}: missing JSON property '{1}'." -f $Label, $Name)
    }
    return $property.Value
}

function Assert-NullProperty {
    param(
        [AllowNull()][object]$Object,
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Label
    )

    $property = Get-JsonProperty $Object $Name $Label
    if ($null -ne $property) {
        throw ("{0}: expected '{1}' to be null." -f $Label, $Name)
    }
}

function Get-ErrorCode {
    param([AllowNull()][object]$Response)

    if ($null -eq $Response -or $null -eq $Response.Json) {
        return ''
    }
    $errorProperty = $Response.Json.PSObject.Properties['error']
    if ($null -eq $errorProperty -or $null -eq $errorProperty.Value) {
        return ''
    }
    $codeProperty = $errorProperty.Value.PSObject.Properties['code']
    if ($null -eq $codeProperty) {
        return ''
    }
    return [string]$codeProperty.Value
}

function Assert-ApiResponse {
    param(
        [Parameter(Mandatory)][object]$Response,
        [Parameter(Mandatory)][int]$Status,
        [Parameter(Mandatory)][string]$Label,
        [string]$ErrorCode
    )

    if ($Response.Status -ne $Status) {
        $actualCode = Get-ErrorCode $Response
        throw ("{0}: expected HTTP {1}, got {2} ({3})." -f $Label, $Status, $Response.Status, $actualCode)
    }
    if ($null -eq $Response.Json) {
        throw ("{0}: response was not a JSON envelope." -f $Label)
    }

    $success = Get-JsonProperty $Response.Json 'success' $Label
    $expectedSuccess = $Status -ge 200 -and $Status -lt 300
    if ([bool]$success -ne $expectedSuccess) {
        throw ("{0}: success flag did not match HTTP status." -f $Label)
    }
    [void](Get-JsonProperty $Response.Json 'timestamp' $Label)

    if ($ErrorCode -and (Get-ErrorCode $Response) -ne $ErrorCode) {
        throw ("{0}: expected error code {1}, got {2}." -f $Label, $ErrorCode, (Get-ErrorCode $Response))
    }
}

function Assert-Equal {
    param(
        [AllowNull()][object]$Actual,
        [AllowNull()][object]$Expected,
        [Parameter(Mandatory)][string]$Label
    )

    if ([string]$Actual -ne [string]$Expected) {
        throw ("{0}: expected '{1}', got '{2}'." -f $Label, $Expected, $Actual)
    }
}

function Assert-True {
    param(
        [Parameter(Mandatory)][bool]$Condition,
        [Parameter(Mandatory)][string]$Label
    )

    if (-not $Condition) {
        throw ("{0}: assertion failed." -f $Label)
    }
}

function Invoke-Api {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [AllowNull()][object]$Body,
        [string]$AccessToken
    )

    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method),
        ($BaseUri + $Path))
    try {
        $request.Headers.Accept.ParseAdd('application/json')
        if ($AccessToken) {
            $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new(
                'Bearer', $AccessToken)
        }
        if ($null -ne $Body) {
            $json = ConvertTo-Json -InputObject $Body -Compress -Depth 10
            $request.Content = [System.Net.Http.StringContent]::new(
                $json, [Text.Encoding]::UTF8, 'application/json')
        }

        $httpResponse = $null
        try {
            $httpResponse = $HttpClient.SendAsync($request).GetAwaiter().GetResult()
            $raw = $httpResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $parsed = $null
            if (-not [string]::IsNullOrWhiteSpace($raw)) {
                try {
                    $parsed = $raw | ConvertFrom-Json
                } catch {
                    $parsed = $null
                }
            }
            return [pscustomobject]@{
                Status = [int]$httpResponse.StatusCode
                Json = $parsed
            }
        } finally {
            if ($null -ne $httpResponse) {
                $httpResponse.Dispose()
            }
        }
    } finally {
        $request.Dispose()
    }
}

function Register-Account {
    param([Parameter(Mandatory)][object]$Account)

    $response = Invoke-Api -Method 'POST' -Path '/api/v1/auth/register' -Body @{
        email = $Account.Email
        password = $Account.Password
        name = $Account.Name
        nickname = $Account.Nickname
        birthDate = $Account.BirthDate
    }
    Assert-ApiResponse $response 201 'register'
}

function Signin-Account {
    param([Parameter(Mandatory)][object]$Account)

    $response = Invoke-Api -Method 'POST' -Path '/api/v1/auth/signin' -Body @{
        email = $Account.Email
        password = $Account.Password
    }
    Assert-ApiResponse $response 200 'signin'
    $data = Get-JsonProperty $response.Json 'data' 'signin'
    $token = Get-JsonProperty $data 'accessToken' 'signin data'
    if ([string]::IsNullOrWhiteSpace([string]$token)) {
        throw 'signin: access token was empty.'
    }
    return [string]$token
}

function Complete-InitialSetup {
    param(
        [Parameter(Mandatory)][object]$Account,
        [Parameter(Mandatory)][string]$AccessToken
    )

    $me = Invoke-Api -Method 'GET' -Path '/api/v1/auth/me' -AccessToken $AccessToken
    Assert-ApiResponse $me 200 'auth/me'
    $meData = Get-JsonProperty $me.Json 'data' 'auth/me'
    $defaultBlog = Get-JsonProperty $meData 'defaultBlog' 'auth/me data'
    $blogId = [long](Get-JsonProperty $defaultBlog 'id' 'default blog')

    $setup = Invoke-Api -Method 'PUT' -Path '/api/v1/blogs/me/initial-setup' -AccessToken $AccessToken -Body @{
        title = $Account.BlogTitle
        urlSlug = $Account.BlogSlug
        description = 'M3 API smoke fixture'
    }
    Assert-ApiResponse $setup 200 'initial-setup'
    $setupData = Get-JsonProperty $setup.Json 'data' 'initial-setup'
    Assert-Equal (Get-JsonProperty $setupData 'id' 'initial-setup data') $blogId 'initial-setup blog id'
    Assert-Equal (Get-JsonProperty $setupData 'urlSlug' 'initial-setup data') $Account.BlogSlug 'initial-setup slug'
    Assert-True ([bool](Get-JsonProperty $setupData 'isSetupCompleted' 'initial-setup data')) 'initial-setup completed'

    $settings = Invoke-Api -Method 'GET' -Path '/api/v1/blogs/me' -AccessToken $AccessToken
    Assert-ApiResponse $settings 200 'blogs/me'
    $settingsData = Get-JsonProperty $settings.Json 'data' 'blogs/me'
    Assert-Equal (Get-JsonProperty $settingsData 'id' 'blogs/me data') $blogId 'settings blog id'
    Assert-Equal (Get-JsonProperty $settingsData 'urlSlug' 'blogs/me data') $Account.BlogSlug 'settings slug'

    return [pscustomobject]@{
        Id = $blogId
        Slug = $Account.BlogSlug
    }
}

function Get-CategoryPage {
    param(
        [Parameter(Mandatory)][long]$BlogId,
        [string]$AccessToken,
        [Parameter(Mandatory)][bool]$IncludeDrafts,
        [Parameter(Mandatory)][string]$Label
    )

    $draftQuery = if ($IncludeDrafts) { '&includeDrafts=true' } else { '' }
    $response = Invoke-Api -Method 'GET' -Path (
        "/api/v1/blogs/{0}/categories?page=0&size=20{1}" -f $BlogId, $draftQuery) -AccessToken $AccessToken
    Assert-ApiResponse $response 200 $Label
    $data = Get-JsonProperty $response.Json 'data' $Label
    return [pscustomobject]@{
        Data = $data
        Items = @((Get-JsonProperty $data 'items' "$Label data"))
    }
}

function Find-Category {
    param(
        [Parameter(Mandatory)][object[]]$Items,
        [Parameter(Mandatory)][long]$Id,
        [Parameter(Mandatory)][string]$Label
    )

    $matches = @($Items | Where-Object {
        [long](Get-JsonProperty $_ 'id' "$Label item") -eq $Id
    })
    if ($matches.Count -ne 1) {
        throw ("{0}: expected exactly one category with id {1}, got {2}." -f $Label, $Id, $matches.Count)
    }
    return $matches[0]
}

try {
    $BaseUri = Assert-LoopbackBaseUri $BaseUri
    $HttpHandler = [System.Net.Http.HttpClientHandler]::new()
    $HttpHandler.UseCookies = $false
    $HttpHandler.AllowAutoRedirect = $false
    $HttpClient = [System.Net.Http.HttpClient]::new($HttpHandler)
    $HttpClient.Timeout = [TimeSpan]::FromSeconds(15)

    $Prefix = 'm3s' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
    $commonPassword = 'M3S-' + [Guid]::NewGuid().ToString('N') + '!aB7'
    $owner = [pscustomobject]@{
        Email = "$Prefix.owner@example.test"
        Password = $commonPassword
        Name = 'M3 Smoke Owner'
        Nickname = "$Prefix-o"
        BirthDate = '1990-01-01'
        BlogTitle = 'M3 Smoke Owner Blog'
        BlogSlug = "$Prefix-owner"
    }
    $viewer = [pscustomobject]@{
        Email = "$Prefix.viewer@example.test"
        Password = $commonPassword
        Name = 'M3 Smoke Viewer'
        Nickname = "$Prefix-v"
        BirthDate = '1991-01-01'
        BlogTitle = 'M3 Smoke Viewer Blog'
        BlogSlug = "$Prefix-viewer"
    }

    Register-Account $owner
    Register-Account $viewer
    $ownerToken = Signin-Account $owner
    $viewerToken = Signin-Account $viewer
    $ownerBlog = Complete-InitialSetup $owner $ownerToken
    [void](Complete-InitialSetup $viewer $viewerToken)

    $anonymousPage = Get-CategoryPage $ownerBlog.Id $null $false 'anonymous category GET'
    $default = @($anonymousPage.Items | Where-Object {
        (Get-JsonProperty $_ 'type' 'anonymous category') -eq 'DEFAULT'
    })[0]
    if ($null -eq $default) {
        throw 'anonymous category GET: default category was missing.'
    }
    Assert-Equal (Get-JsonProperty $default 'name' 'default category') '미분류' 'default category name'
    Assert-NullProperty $default 'parentId' 'root category parentId'

    $ownerPage = Get-CategoryPage $ownerBlog.Id $ownerToken $true 'owner category GET with drafts'
    $ownerDefault = Find-Category $ownerPage.Items ([long](Get-JsonProperty $default 'id' 'default category')) 'owner default'

    $nonOwnerDrafts = Invoke-Api -Method 'GET' -Path (
        "/api/v1/blogs/{0}/categories?page=0&size=20&includeDrafts=true" -f $ownerBlog.Id) -AccessToken $viewerToken
    Assert-ApiResponse $nonOwnerDrafts 403 'non-owner includeDrafts' 'CAT_004'

    $anonymousWrite = Invoke-Api -Method 'POST' -Path ("/api/v1/blogs/{0}/categories" -f $ownerBlog.Id) -Body @{
        name = 'Unauthenticated'
        parentId = $null
        type = 'GENERAL'
        displayOrder = 99
    }
    Assert-ApiResponse $anonymousWrite 401 'anonymous category write' 'AUTH_004'

    $nonOwnerWrite = Invoke-Api -Method 'POST' -Path ("/api/v1/blogs/{0}/categories" -f $ownerBlog.Id) -AccessToken $viewerToken -Body @{
        name = 'NonOwner'
        parentId = $null
        type = 'GENERAL'
        displayOrder = 99
    }
    Assert-ApiResponse $nonOwnerWrite 403 'non-owner category write' 'CAT_004'

    $rootCreate = Invoke-Api -Method 'POST' -Path ("/api/v1/blogs/{0}/categories" -f $ownerBlog.Id) -AccessToken $ownerToken -Body @{
        name = ' Root '
        parentId = $null
        type = 'GENERAL'
        displayOrder = 1
    }
    Assert-ApiResponse $rootCreate 201 'root category create'
    $root = Get-JsonProperty $rootCreate.Json 'data' 'root category create'
    $rootId = [long](Get-JsonProperty $root 'id' 'root category')
    Assert-Equal (Get-JsonProperty $root 'name' 'root category') 'Root' 'trimmed root name'

    $childCreate = Invoke-Api -Method 'POST' -Path ("/api/v1/blogs/{0}/categories" -f $ownerBlog.Id) -AccessToken $ownerToken -Body @{
        name = 'Child'
        parentId = $rootId
        type = 'GENERAL'
        displayOrder = 0
    }
    Assert-ApiResponse $childCreate 201 'child category create'
    $child = Get-JsonProperty $childCreate.Json 'data' 'child category create'
    $childId = [long](Get-JsonProperty $child 'id' 'child category')
    Assert-Equal (Get-JsonProperty $child 'parentId' 'child category') $rootId 'child parentId'

    $boundaryName = ('N' * 100) -join ''
    $boundaryCreate = Invoke-Api -Method 'POST' -Path ("/api/v1/blogs/{0}/categories" -f $ownerBlog.Id) -AccessToken $ownerToken -Body @{
        name = " $boundaryName "
        parentId = $rootId
        type = 'GENERAL'
        displayOrder = 1
    }
    Assert-ApiResponse $boundaryCreate 201 'trimmed 100-character category'
    $boundary = Get-JsonProperty $boundaryCreate.Json 'data' 'trimmed 100-character category'
    Assert-Equal (Get-JsonProperty $boundary 'name' 'trimmed 100-character category') $boundaryName 'trimmed 100-character name'

    $lockedCreate = Invoke-Api -Method 'POST' -Path ("/api/v1/blogs/{0}/categories" -f $ownerBlog.Id) -AccessToken $ownerToken -Body @{
        name = 'LockedRoot'
        parentId = $null
        type = 'GENERAL'
        displayOrder = 2
    }
    Assert-ApiResponse $lockedCreate 201 'locked candidate create'
    $locked = Get-JsonProperty $lockedCreate.Json 'data' 'locked candidate create'
    $lockedId = [long](Get-JsonProperty $locked 'id' 'locked candidate')

    $ownerTree = Get-CategoryPage $ownerBlog.Id $ownerToken $true 'owner tree after create'
    $treeRoot = Find-Category $ownerTree.Items $rootId 'root tree item'
    $treeChild = @((Get-JsonProperty $treeRoot 'children' 'root tree item') | Where-Object {
        [long](Get-JsonProperty $_ 'id' 'child tree item') -eq $childId
    })[0]
    if ($null -eq $treeChild) {
        throw 'owner tree after create: child was not included under its root.'
    }
    Assert-Equal (Get-JsonProperty $treeChild 'parentId' 'child tree item') $rootId 'tree child parentId'

    $lock = Invoke-Api -Method 'PUT' -Path ("/api/v1/blogs/{0}/categories/{1}" -f $ownerBlog.Id, $lockedId) -AccessToken $ownerToken -Body @{
        name = 'LockedRoot'
        type = 'LOCKED'
        displayOrder = 2
    }
    Assert-ApiResponse $lock 200 'GENERAL to LOCKED'
    $lockedData = Get-JsonProperty $lock.Json 'data' 'GENERAL to LOCKED'
    Assert-Equal (Get-JsonProperty $lockedData 'type' 'locked category') 'LOCKED' 'locked type'

    $lockedIdempotent = Invoke-Api -Method 'PUT' -Path ("/api/v1/blogs/{0}/categories/{1}" -f $ownerBlog.Id, $lockedId) -AccessToken $ownerToken -Body @{
        name = 'LockedRoot'
        type = 'LOCKED'
        displayOrder = 2
    }
    Assert-ApiResponse $lockedIdempotent 200 'LOCKED idempotent update'

    $lockedOrderChange = Invoke-Api -Method 'PUT' -Path ("/api/v1/blogs/{0}/categories/{1}" -f $ownerBlog.Id, $lockedId) -AccessToken $ownerToken -Body @{
        name = 'LockedRoot'
        type = 'LOCKED'
        displayOrder = 3
    }
    Assert-ApiResponse $lockedOrderChange 400 'LOCKED order change' 'CAT_006'

    $defaultCreate = Invoke-Api -Method 'POST' -Path ("/api/v1/blogs/{0}/categories" -f $ownerBlog.Id) -AccessToken $ownerToken -Body @{
        name = 'InvalidDefault'
        parentId = $null
        type = 'DEFAULT'
        displayOrder = 3
    }
    Assert-ApiResponse $defaultCreate 400 'DEFAULT category create' 'CAT_006'

    $duplicate = Invoke-Api -Method 'POST' -Path ("/api/v1/blogs/{0}/categories" -f $ownerBlog.Id) -AccessToken $ownerToken -Body @{
        name = ' Root '
        parentId = $null
        type = 'GENERAL'
        displayOrder = 3
    }
    Assert-ApiResponse $duplicate 409 'duplicate category name' 'CAT_005'

    $lockedMove = Invoke-Api -Method 'PUT' -Path ("/api/v1/blogs/{0}/categories/order" -f $ownerBlog.Id) -AccessToken $ownerToken -Body ([long[]]@($lockedId, [long](Get-JsonProperty $ownerDefault 'id' 'default category'), $rootId))
    Assert-ApiResponse $lockedMove 400 'LOCKED position change' 'CAT_006'

    $defaultId = [long](Get-JsonProperty $ownerDefault 'id' 'default category')
    $validOrder = Invoke-Api -Method 'PUT' -Path ("/api/v1/blogs/{0}/categories/order" -f $ownerBlog.Id) -AccessToken $ownerToken -Body ([long[]]@($rootId, $defaultId, $lockedId))
    Assert-ApiResponse $validOrder 200 'DEFAULT order change'

    $afterOrder = Get-CategoryPage $ownerBlog.Id $ownerToken $true 'owner tree after reorder'
    $afterDefault = Find-Category $afterOrder.Items $defaultId 'default after reorder'
    $afterRoot = Find-Category $afterOrder.Items $rootId 'root after reorder'
    $afterLocked = Find-Category $afterOrder.Items $lockedId 'locked after reorder'
    Assert-Equal (Get-JsonProperty $afterDefault 'displayOrder' 'default after reorder') 1 'DEFAULT order'
    Assert-Equal (Get-JsonProperty $afterRoot 'displayOrder' 'root after reorder') 0 'GENERAL order'
    Assert-Equal (Get-JsonProperty $afterLocked 'displayOrder' 'locked after reorder') 2 'LOCKED order preserved'

    $defaultRename = Invoke-Api -Method 'PUT' -Path ("/api/v1/blogs/{0}/categories/{1}" -f $ownerBlog.Id, $defaultId) -AccessToken $ownerToken -Body @{
        name = 'RenamedDefault'
        type = 'DEFAULT'
        displayOrder = 1
    }
    Assert-ApiResponse $defaultRename 400 'DEFAULT rename' 'CAT_006'

    $defaultDelete = Invoke-Api -Method 'DELETE' -Path ("/api/v1/blogs/{0}/categories/{1}" -f $ownerBlog.Id, $defaultId) -AccessToken $ownerToken
    Assert-ApiResponse $defaultDelete 400 'DEFAULT delete' 'CAT_003'

    $rootDelete = Invoke-Api -Method 'DELETE' -Path ("/api/v1/blogs/{0}/categories/{1}" -f $ownerBlog.Id, $rootId) -AccessToken $ownerToken
    Assert-ApiResponse $rootDelete 200 'GENERAL subtree delete'

    $afterDelete = Get-CategoryPage $ownerBlog.Id $ownerToken $true 'owner tree after subtree delete'
    if (@($afterDelete.Items | Where-Object {
        [long](Get-JsonProperty $_ 'id' 'deleted root check') -eq $rootId
    }).Count -ne 0) {
        throw 'GENERAL subtree delete: deleted root remained active.'
    }

    $recreated = Invoke-Api -Method 'POST' -Path ("/api/v1/blogs/{0}/categories" -f $ownerBlog.Id) -AccessToken $ownerToken -Body @{
        name = 'Root'
        parentId = $null
        type = 'GENERAL'
        displayOrder = 0
    }
    Assert-ApiResponse $recreated 201 'deleted name/order reuse'
    $recreatedData = Get-JsonProperty $recreated.Json 'data' 'deleted name/order reuse'
    $recreatedId = [long](Get-JsonProperty $recreatedData 'id' 'recreated category')
    Assert-Equal (Get-JsonProperty $recreatedData 'name' 'recreated category') 'Root' 'recreated name'
    Assert-Equal (Get-JsonProperty $recreatedData 'displayOrder' 'recreated category') 0 'recreated order'

    $staleOrder = Invoke-Api -Method 'PUT' -Path ("/api/v1/blogs/{0}/categories/order" -f $ownerBlog.Id) -AccessToken $ownerToken -Body ([long[]]@($defaultId, $lockedId))
    Assert-ApiResponse $staleOrder 400 'stale order set' 'CAT_007'

    Write-Host ("M3 API smoke passed. Fixture prefix: {0}. Accounts remain for cleanup." -f $Prefix)
} catch {
    Write-Error ("M3 API smoke failed: {0}" -f $_.Exception.Message)
    exit 1
} finally {
    if ($null -ne $HttpClient) {
        $HttpClient.Dispose()
    }
    if ($null -ne $HttpHandler) {
        $HttpHandler.Dispose()
    }
}
