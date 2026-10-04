<#
.SYNOPSIS
    M4 local HTTP acceptance smoke for posts and local image content.

.DESCRIPTION
    Uses existing local fixture accounts supplied as parameters. It does not
    register accounts, create Universe relations, run SQL, or start/stop
    services. It deliberately soft-deletes one generated private post to verify
    the post/image negative path; it never physically deletes existing or
    generated files, posts, accounts, or fixtures. Credentials and response
    bodies are kept in memory and are never written to output.

    This is an independent API smoke, not the complete M4 acceptance. The
    companion qa/M4-review.md lists the required MySQL, restart, browser, FE,
    OpenAPI, and 24-hour evidence that this script intentionally does not
    replace.
#>
[CmdletBinding()]
param(
    [string]$BaseUri = 'http://127.0.0.1:8080',
    [Parameter(Mandatory)][string]$OwnerEmail,
    [Parameter(Mandatory)][string]$OwnerPassword,
    [Parameter(Mandatory)][string]$ForwardViewerEmail,
    [Parameter(Mandatory)][string]$ForwardViewerPassword,
    [Parameter(Mandatory)][string]$ReverseViewerEmail,
    [Parameter(Mandatory)][string]$ReverseViewerPassword,
    [Parameter(Mandatory)][string]$UnrelatedViewerEmail,
    [Parameter(Mandatory)][string]$UnrelatedViewerPassword
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$HttpClient = $null
$HttpHandler = $null
$AnonymousUserAgent = 'ZeroVerse-M4-QA/1.0'
$FiveMiB = 5 * 1024 * 1024

function Assert-LoopbackBaseUri {
    param([Parameter(Mandatory)][string]$Value)

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
        throw ("{0}: expected HTTP {1}, got {2} ({3})." -f $Label, $Status, $Response.Status, (Get-ErrorCode $Response))
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

function Assert-TextNotContains {
    param(
        [Parameter(Mandatory)][string]$Text,
        [Parameter(Mandatory)][string]$Needle,
        [Parameter(Mandatory)][string]$Label
    )

    Assert-True ($Text.IndexOf($Needle, [StringComparison]::OrdinalIgnoreCase) -lt 0) $Label
}

function Assert-Null {
    param(
        [AllowNull()][object]$Value,
        [Parameter(Mandatory)][string]$Label
    )

    if ($null -ne $Value) {
        throw ("{0}: expected null, got '{1}'." -f $Label, $Value)
    }
}

function Invoke-Request {
    param(
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [AllowNull()][object]$Body,
        [string]$AccessToken,
        [string]$RawAuthorization,
        [switch]$Multipart,
        [AllowNull()][AllowEmptyCollection()][byte[]]$FileBytes,
        [string]$FileName = 'qa.png',
        [string]$FileContentType = 'image/png',
        [string]$Purpose
    )

    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::new($Method),
        ($BaseUri + $Path))
    try {
        $request.Headers.Accept.ParseAdd('application/json')
        $request.Headers.UserAgent.ParseAdd($AnonymousUserAgent)
        if ($RawAuthorization) {
            [void]$request.Headers.TryAddWithoutValidation('Authorization', $RawAuthorization)
        } elseif ($AccessToken) {
            $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new(
                'Bearer', $AccessToken)
        }

        if ($Multipart) {
            if ($null -eq $FileBytes -or $null -eq $Purpose) {
                throw 'Multipart request requires FileBytes and Purpose.'
            }
            $multipartContent = [System.Net.Http.MultipartFormDataContent]::new()
            $fileContent = [System.Net.Http.ByteArrayContent]::new($FileBytes)
            $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new($FileContentType)
            $multipartContent.Add($fileContent, 'file', $FileName)
            $purposeContent = [System.Net.Http.StringContent]::new($Purpose)
            $multipartContent.Add($purposeContent, 'purpose')
            $request.Content = $multipartContent
        } elseif ($null -ne $Body) {
            $json = ConvertTo-Json -InputObject $Body -Compress -Depth 30
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
                Raw = $raw
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

function Invoke-Binary {
    param(
        [Parameter(Mandatory)][string]$Path,
        [string]$AccessToken,
        [string]$RawAuthorization
    )

    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Get, ($BaseUri + $Path))
    try {
        $request.Headers.UserAgent.ParseAdd($AnonymousUserAgent)
        if ($RawAuthorization) {
            [void]$request.Headers.TryAddWithoutValidation('Authorization', $RawAuthorization)
        } elseif ($AccessToken) {
            $request.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new(
                'Bearer', $AccessToken)
        }
        $httpResponse = $null
        try {
            $httpResponse = $HttpClient.SendAsync($request).GetAwaiter().GetResult()
            $bytes = $httpResponse.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
            $headers = @{}
            foreach ($header in $httpResponse.Headers) {
                $headers[$header.Key] = [string]::Join(', ', $header.Value)
            }
            foreach ($header in $httpResponse.Content.Headers) {
                $headers[$header.Key] = [string]::Join(', ', $header.Value)
            }
            return [pscustomobject]@{
                Status = [int]$httpResponse.StatusCode
                Bytes = [byte[]]$bytes
                Headers = $headers
                Raw = [Text.Encoding]::UTF8.GetString($bytes)
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

function Get-Header {
    param(
        [Parameter(Mandatory)][hashtable]$Headers,
        [Parameter(Mandatory)][string]$Name
    )

    foreach ($key in $Headers.Keys) {
        if ([string]::Equals([string]$key, $Name, [StringComparison]::OrdinalIgnoreCase)) {
            return [string]$Headers[$key]
        }
    }
    return ''
}

function Assert-BinarySuccess {
    param(
        [Parameter(Mandatory)][object]$Response,
        [Parameter(Mandatory)][string]$Label,
        [Parameter(Mandatory)][string]$ContentType
    )

    Assert-Equal $Response.Status 200 $Label
    Assert-True ($Response.Bytes.Length -gt 0) ("{0} bytes" -f $Label)
    $actualType = Get-Header $Response.Headers 'Content-Type'
    Assert-True ($actualType.ToLowerInvariant().StartsWith($ContentType.ToLowerInvariant())) ("{0} content type" -f $Label)
    $cacheControl = (Get-Header $Response.Headers 'Cache-Control').ToLowerInvariant()
    Assert-True ($cacheControl.Contains('no-store')) ("{0} cache policy" -f $Label)
    Assert-Equal (Get-Header $Response.Headers 'X-Content-Type-Options').ToLowerInvariant() 'nosniff' ("{0} nosniff" -f $Label)
}

function New-ImageBytes {
    param(
        [Parameter(Mandatory)][ValidateSet('jpeg','png','webp','gif')][string]$Kind,
        [Parameter(Mandatory)][int]$Size
    )

    if ($Size -lt 1) {
        return ,([byte[]]::new(0))
    }
    [byte[]]$seed = switch ($Kind) {
        'jpeg' { [byte[]](0xFF,0xD8,0xFF,0xE0,0x00,0x10,0x4A,0x46,0x49,0x46,0x00,0x01,0xFF,0xD9) }
        'png'  { [byte[]](0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A,0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52) }
        'webp' { [byte[]](0x52,0x49,0x46,0x46,0x1A,0x00,0x00,0x00,0x57,0x45,0x42,0x50,0x56,0x50,0x38,0x20) }
        'gif'  { [byte[]](0x47,0x49,0x46,0x38,0x39,0x61,0x01,0x00,0x01,0x00,0x80,0x00,0x00,0x00,0x00,0x00,0xFF,0xFF,0xFF,0x21,0xF9,0x04,0x01,0x00,0x00,0x00,0x00,0x2C,0x00,0x00,0x00,0x00,0x01,0x00,0x01,0x00,0x00,0x02,0x02,0x44,0x01,0x00,0x3B) }
    }
    if ($seed.Length -gt $Size) {
        throw ("{0} seed is larger than requested size {1}." -f $Kind, $Size)
    }
    $bytes = [byte[]]::new($Size)
    [Buffer]::BlockCopy($seed, 0, $bytes, 0, $seed.Length)
    return ,$bytes
}

function Get-MimeForKind {
    param([Parameter(Mandatory)][string]$Kind)
    switch ($Kind) {
        'jpeg' { return 'image/jpeg' }
        'png'  { return 'image/png' }
        'webp' { return 'image/webp' }
        'gif'  { return 'image/gif' }
    }
}

function Signin-ExistingAccount {
    param(
        [Parameter(Mandatory)][string]$Email,
        [Parameter(Mandatory)][string]$Password,
        [Parameter(Mandatory)][string]$Label
    )

    $signin = Invoke-Request -Method 'POST' -Path '/api/v1/auth/signin' -Body @{
        email = $Email
        password = $Password
    }
    Assert-ApiResponse $signin 200 ("{0} signin" -f $Label)
    $data = Get-JsonProperty $signin.Json 'data' ("{0} signin data" -f $Label)
    $token = [string](Get-JsonProperty $data 'accessToken' ("{0} access token" -f $Label))
    Assert-True (-not [string]::IsNullOrWhiteSpace($token)) ("{0} access token" -f $Label)

    $me = Invoke-Request -Method 'GET' -Path '/api/v1/auth/me' -AccessToken $token
    Assert-ApiResponse $me 200 ("{0} auth/me" -f $Label)
    $meData = Get-JsonProperty $me.Json 'data' ("{0} auth/me data" -f $Label)
    $blog = Get-JsonProperty $meData 'defaultBlog' ("{0} default blog" -f $Label)
    $blogId = [long](Get-JsonProperty $blog 'id' ("{0} blog id" -f $Label))
    $slug = [string](Get-JsonProperty $blog 'urlSlug' ("{0} blog slug" -f $Label))
    $setupCompleted = Get-JsonProperty $blog 'isSetupCompleted' ("{0} setup state" -f $Label)
    Assert-True ([bool]$setupCompleted) ("{0} active setup" -f $Label)

    return [pscustomobject]@{
        Label = $Label
        Email = $Email
        Token = $token
        BlogId = $blogId
        BlogSlug = $slug
    }
}

function Get-DefaultCategoryId {
    param([Parameter(Mandatory)][object]$Owner)

    $response = Invoke-Request -Method 'GET' -Path (
        '/api/v1/blogs/{0}/categories?page=0&size=100&includeDrafts=true' -f $Owner.BlogId) -AccessToken $Owner.Token
    Assert-ApiResponse $response 200 'owner categories'
    $data = Get-JsonProperty $response.Json 'data' 'owner categories data'
    $items = @((Get-JsonProperty $data 'items' 'owner categories items'))
    $defaults = @($items | Where-Object {
        (Get-JsonProperty $_ 'type' 'category type') -eq 'DEFAULT'
    })
    if ($defaults.Count -ne 1) {
        throw ("owner categories: expected one DEFAULT, got {0}." -f $defaults.Count)
    }
    return [long](Get-JsonProperty $defaults[0] 'id' 'default category id')
}

function Upload-Image {
    param(
        [Parameter(Mandatory)][object]$Session,
        [Parameter(Mandatory)][AllowEmptyCollection()][byte[]]$Bytes,
        [Parameter(Mandatory)][string]$Kind,
        [Parameter(Mandatory)][ValidateSet('POST_IMAGE','POST_THUMBNAIL','PROFILE_IMAGE')][string]$Purpose,
        [Parameter(Mandatory)][string]$Label,
        [int]$ExpectedStatus = 201,
        [string]$ExpectedErrorCode
    )

    $contentType = Get-MimeForKind $Kind
    $response = Invoke-Request -Method 'POST' -Path '/api/v1/uploads' -AccessToken $Session.Token `
        -Multipart -FileBytes $Bytes -FileName ("{0}.{1}" -f $Label, $Kind) -FileContentType $contentType -Purpose $Purpose
    Assert-ApiResponse $response $ExpectedStatus $Label $ExpectedErrorCode
    if ($ExpectedStatus -lt 300) {
        $data = Get-JsonProperty $response.Json 'data' ("{0} data" -f $Label)
        $id = [string](Get-JsonProperty $data 'id' ("{0} id" -f $Label))
        $imageUrl = [string](Get-JsonProperty $data 'imageUrl' ("{0} image URL" -f $Label))
        Assert-True (-not [string]::IsNullOrWhiteSpace($id)) ("{0} id" -f $Label)
        Assert-True ($imageUrl.StartsWith('/api/v1/uploads/') -and $imageUrl.EndsWith('/content')) ("{0} canonical image URL" -f $Label)
        Assert-True (-not $imageUrl.StartsWith('http') -and -not $imageUrl.StartsWith('blob:')) ("{0} non-external image URL" -f $Label)
        Assert-Equal (Get-JsonProperty $data 'contentType' ("{0} content type" -f $Label)) $contentType ("{0} metadata content type" -f $Label)
        Assert-Equal ([int](Get-JsonProperty $data 'size' ("{0} size" -f $Label)) ) $Bytes.Length ("{0} metadata size" -f $Label)
        Assert-Equal (Get-JsonProperty $data 'purpose' ("{0} purpose" -f $Label)) $Purpose ("{0} metadata purpose" -f $Label)
        return [pscustomobject]@{
            Id = $id
            ImageUrl = $imageUrl
            ContentType = $contentType
            Size = $Bytes.Length
            Purpose = $Purpose
            Bytes = $Bytes
        }
    }
    return $null
}

function New-ContentJson {
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string]$Text,
        [string]$ImageUrl
    )

    $paragraph = @{ type = 'paragraph'; content = @(@{ type = 'text'; text = $Text }) }
    $content = @($paragraph)
    if ($ImageUrl) {
        $content += @{ type = 'image'; attrs = @{ src = $ImageUrl; alt = 'M4 QA image' } }
    }
    return @{ type = 'doc'; content = $content }
}

function New-ContentJsonWithImages {
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string]$Text,
        [Parameter(Mandatory)][AllowEmptyCollection()][string[]]$ImageUrls
    )

    $paragraph = @{ type = 'paragraph'; content = @(@{ type = 'text'; text = $Text }) }
    $content = @($paragraph)
    foreach ($imageUrl in $ImageUrls) {
        $content += @{ type = 'image'; attrs = @{ src = $imageUrl; alt = 'M4 QA image' } }
    }
    return @{ type = 'doc'; content = $content }
}

function New-Post {
    param(
        [Parameter(Mandatory)][object]$Owner,
        [Parameter(Mandatory)][AllowNull()][Nullable[long]]$CategoryId,
        [Parameter(Mandatory)][AllowEmptyString()][string]$Title,
        [Parameter(Mandatory)][object]$ContentJson,
        [Parameter(Mandatory)][string]$ContentHtml,
        [Parameter(Mandatory)][ValidateSet('PUBLIC','UNIVERSE','PRIVATE')][string]$Visibility,
        [Parameter(Mandatory)][bool]$Publish,
        [AllowNull()][object]$ThumbnailUrl,
        [AllowEmptyCollection()][object[]]$TagNames = @(),
        [AllowEmptyCollection()][object[]]$Images = @(),
        [Parameter(Mandatory)][string]$Label,
        [int]$ExpectedStatus = 201,
        [string]$ExpectedErrorCode
    )

    $body = @{
        blogId = $Owner.BlogId
        title = $Title
        contentJson = $ContentJson
        contentHtml = $ContentHtml
        categoryId = $CategoryId
        visibility = $Visibility
        publish = $Publish
        thumbnailUrl = $ThumbnailUrl
        tagNames = @($TagNames)
        images = @($Images)
    }
    $response = Invoke-Request -Method 'POST' -Path '/api/v1/posts' -AccessToken $Owner.Token -Body $body
    Assert-ApiResponse $response $ExpectedStatus $Label $ExpectedErrorCode
    if ($ExpectedStatus -lt 300) {
        $data = Get-JsonProperty $response.Json 'data' ("{0} data" -f $Label)
        return $data
    }
    return $null
}

function Get-PostDetail {
    param(
        [Parameter(Mandatory)][long]$PostId,
        [string]$AccessToken,
        [Parameter(Mandatory)][string]$Label,
        [int]$ExpectedStatus = 200,
        [string]$ExpectedErrorCode,
        [string]$RawAuthorization
    )

    $response = Invoke-Request -Method 'GET' -Path ("/api/v1/posts/{0}" -f $PostId) `
        -AccessToken $AccessToken -RawAuthorization $RawAuthorization
    Assert-ApiResponse $response $ExpectedStatus $Label $ExpectedErrorCode
    if ($ExpectedStatus -lt 300) {
        return (Get-JsonProperty $response.Json 'data' ("{0} data" -f $Label))
    }
    return $null
}

function Get-PostList {
    param(
        [Parameter(Mandatory)][string]$Path,
        [string]$AccessToken,
        [Parameter(Mandatory)][string]$Label,
        [int]$ExpectedStatus = 200,
        [string]$ExpectedErrorCode
    )

    $response = Invoke-Request -Method 'GET' -Path $Path -AccessToken $AccessToken
    Assert-ApiResponse $response $ExpectedStatus $Label $ExpectedErrorCode
    if ($ExpectedStatus -lt 300) {
        $data = Get-JsonProperty $response.Json 'data' ("{0} data" -f $Label)
        return ,@((Get-JsonProperty $data 'items' ("{0} items" -f $Label)))
    }
    return ,@()
}

function Get-PostImages {
    param([AllowNull()][object]$Detail, [Parameter(Mandatory)][string]$Label)
    return ,@((Get-JsonProperty $Detail 'images' $Label))
}

function Assert-ContainsId {
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Items,
        [Parameter(Mandatory)][long]$Id,
        [Parameter(Mandatory)][string]$Label
    )
    $count = @($Items | Where-Object { [long](Get-JsonProperty $_ 'id' "$Label item") -eq $Id }).Count
    Assert-Equal $count 1 $Label
}

function Assert-ExcludesId {
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Items,
        [Parameter(Mandatory)][long]$Id,
        [Parameter(Mandatory)][string]$Label
    )
    $count = @($Items | Where-Object { [long](Get-JsonProperty $_ 'id' "$Label item") -eq $Id }).Count
    Assert-Equal $count 0 $Label
}

function Assert-SanitizedDetail {
    param(
        [Parameter(Mandatory)][object]$Detail,
        [Parameter(Mandatory)][string]$CanonicalImageUrl,
        [Parameter(Mandatory)][string]$Label
    )

    $html = [string](Get-JsonProperty $Detail 'contentHtml' "$Label HTML")
    Assert-TextNotContains $html '<script' "$Label script removed"
    Assert-TextNotContains $html 'onerror' "$Label event removed"
    Assert-TextNotContains $html 'javascript:' "$Label javascript URL removed"
    Assert-TextNotContains $html '<iframe' "$Label iframe removed"

    $json = Get-JsonProperty $Detail 'contentJson' "$Label JSON"
    $jsonText = $json | ConvertTo-Json -Compress -Depth 30
    Assert-True ($jsonText.Contains($CanonicalImageUrl)) "$Label canonical image retained in JSON"
    $images = Get-PostImages $Detail "$Label images"
    Assert-Equal $images.Count 1 "$Label image snapshot count"
    Assert-Equal (Get-JsonProperty $images[0] 'imageUrl' "$Label image snapshot") $CanonicalImageUrl "$Label image snapshot URL"
}

function Update-PostSnapshot {
    param(
        [Parameter(Mandatory)][object]$Owner,
        [Parameter(Mandatory)][object]$Detail,
        [Parameter(Mandatory)][object]$ContentJson,
        [Parameter(Mandatory)][string]$ContentHtml,
        [Parameter(Mandatory)][bool]$Publish,
        [AllowNull()][object]$ThumbnailUrl,
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]]$Images,
        [Parameter(Mandatory)][string]$Label,
        [AllowNull()][string]$Title
    )

    $category = Get-JsonProperty $Detail 'category' "$Label category"
    $categoryId = [long](Get-JsonProperty $category 'id' "$Label category id")
    $tags = @((Get-JsonProperty $Detail 'tags' "$Label tags"))
    $requestedTitle = if ($PSBoundParameters.ContainsKey('Title')) { $Title } else { Get-JsonProperty $Detail 'title' "$Label title" }
    $response = Invoke-Request -Method 'PUT' -Path ("/api/v1/posts/{0}" -f [long](Get-JsonProperty $Detail 'id' "$Label id")) `
        -AccessToken $Owner.Token -Body @{
            title = $requestedTitle
            contentJson = $ContentJson
            contentHtml = $ContentHtml
            categoryId = $categoryId
            visibility = Get-JsonProperty $Detail 'visibility' "$Label visibility"
            publish = $Publish
            thumbnailUrl = $ThumbnailUrl
            tagNames = $tags
            images = $Images
        }
    Assert-ApiResponse $response 200 $Label
    return (Get-JsonProperty $response.Json 'data' "$Label data")
}

try {
    $BaseUri = Assert-LoopbackBaseUri $BaseUri
    $HttpHandler = [System.Net.Http.HttpClientHandler]::new()
    $HttpHandler.UseCookies = $false
    $HttpHandler.AllowAutoRedirect = $false
    $HttpClient = [System.Net.Http.HttpClient]::new($HttpHandler)
    $HttpClient.Timeout = [TimeSpan]::FromSeconds(30)

    $owner = Signin-ExistingAccount $OwnerEmail $OwnerPassword 'owner'
    $forward = Signin-ExistingAccount $ForwardViewerEmail $ForwardViewerPassword 'forward viewer'
    $reverse = Signin-ExistingAccount $ReverseViewerEmail $ReverseViewerPassword 'reverse viewer'
    $unrelated = Signin-ExistingAccount $UnrelatedViewerEmail $UnrelatedViewerPassword 'unrelated viewer'
    $defaultCategoryId = Get-DefaultCategoryId $owner

    $smallPng = New-ImageBytes 'png' 128
    $validPng = Upload-Image $owner $smallPng 'png' 'POST_IMAGE' 'public image'
    $publicThumbnail = Upload-Image $owner (New-ImageBytes 'jpeg' 128) 'jpeg' 'POST_THUMBNAIL' 'public thumbnail'
    $universeImage = Upload-Image $owner (New-ImageBytes 'gif' 128) 'gif' 'POST_IMAGE' 'universe image'
    $privateImage = Upload-Image $owner (New-ImageBytes 'webp' 128) 'webp' 'POST_IMAGE' 'private image'
    $draftImage = Upload-Image $owner (New-ImageBytes 'png' 128) 'png' 'POST_IMAGE' 'draft image'

    $noAuthUpload = Invoke-Request -Method 'POST' -Path '/api/v1/uploads' -Multipart `
        -FileBytes $smallPng -FileName 'no-auth.png' -FileContentType 'image/png' -Purpose 'POST_IMAGE'
    Assert-ApiResponse $noAuthUpload 401 'anonymous upload' 'AUTH_004'

    $emptyUpload = Upload-Image $owner ([byte[]]::new(0)) 'png' 'POST_IMAGE' 'empty image' 400 'UPLOAD_002'
    $spoofUpload = Upload-Image $owner ([Text.Encoding]::UTF8.GetBytes('not an image')) 'png' 'POST_IMAGE' 'MIME spoof' 400 'UPLOAD_001'
    $exactFiveMiB = Upload-Image $owner (New-ImageBytes 'png' $FiveMiB) 'png' 'POST_IMAGE' 'exact-5mib'
    $overFiveMiB = Upload-Image $owner (New-ImageBytes 'png' ($FiveMiB + 1)) 'png' 'POST_IMAGE' 'over-5mib' 400 'UPLOAD_002'

    $duplicateOrderImage = Upload-Image $owner (New-ImageBytes 'png' 128) 'png' 'POST_IMAGE' 'duplicate-order-image'
    $duplicateOrderJson = New-ContentJsonWithImages 'duplicate order' @($exactFiveMiB.ImageUrl, $duplicateOrderImage.ImageUrl)
    $duplicateOrderPost = New-Post $owner $defaultCategoryId 'M4 QA duplicate order rejected' $duplicateOrderJson '<p>duplicate order</p>' `
        'PUBLIC' $true $null @() @(
            @{ imageUrl = $exactFiveMiB.ImageUrl; altText = 'first'; displayOrder = 0 },
            @{ imageUrl = $duplicateOrderImage.ImageUrl; altText = 'second'; displayOrder = 0 }
        ) 'duplicate image order' 400 'VALIDATION_001'

    $duplicateUrlJson = New-ContentJsonWithImages 'duplicate URL dedupe' @($exactFiveMiB.ImageUrl)
    $duplicateUrlPost = New-Post $owner $defaultCategoryId 'M4 QA duplicate URL deduped' $duplicateUrlJson '<p>duplicate URL dedupe</p>' `
        'PUBLIC' $true $null @() @(
            @{ imageUrl = $exactFiveMiB.ImageUrl; altText = 'first'; displayOrder = 0 },
            @{ imageUrl = $exactFiveMiB.ImageUrl; altText = 'duplicate'; displayOrder = 1 }
        ) 'duplicate image URL deduped' 201
    $duplicateUrlImages = Get-PostImages $duplicateUrlPost 'duplicate URL post images'
    Assert-Equal $duplicateUrlImages.Count 1 'duplicate URL deduplicated image count'
    Assert-Equal (Get-JsonProperty $duplicateUrlImages[0] 'imageUrl' 'duplicate URL image URL') $exactFiveMiB.ImageUrl 'duplicate URL retained once'
    Assert-Equal (Get-JsonProperty $duplicateUrlImages[0] 'displayOrder' 'duplicate URL order') 0 'duplicate URL re-numbered from zero'

    $nonContiguousOrderJson = New-ContentJsonWithImages 'non-contiguous order' @($exactFiveMiB.ImageUrl, $duplicateOrderImage.ImageUrl)
    $nonContiguousOrderPost = New-Post $owner $defaultCategoryId 'M4 QA non-contiguous order rejected' $nonContiguousOrderJson '<p>non-contiguous order</p>' `
        'PUBLIC' $true $null @() @(
            @{ imageUrl = $exactFiveMiB.ImageUrl; altText = 'first'; displayOrder = 7 },
            @{ imageUrl = $duplicateOrderImage.ImageUrl; altText = 'second'; displayOrder = 12 }
        ) 'non-contiguous image order' 400 'VALIDATION_001'

    foreach ($kind in @('jpeg','png','webp','gif')) {
        $fourMimeUpload = Upload-Image $owner (New-ImageBytes $kind 128) $kind 'POST_IMAGE' ("allowed {0}" -f $kind)
        Assert-Equal $fourMimeUpload.ContentType (Get-MimeForKind $kind) ("allowed MIME {0}" -f $kind)
    }

    $publicTitle = "M4 QA public $([Guid]::NewGuid().ToString('N').Substring(0, 8))"
    $publicJson = New-ContentJson 'public text' $validPng.ImageUrl
    $public = New-Post $owner $null $publicTitle $publicJson `
        '<p>safe</p><script>alert(1)</script><img src="javascript:bad()" onerror="alert(1)"><iframe src="https://evil.invalid"></iframe>' `
        'PUBLIC' $true $publicThumbnail.ImageUrl @(' QA-M4 ', 'qa-m4', 'Second') `
        @(@{ imageUrl = $validPng.ImageUrl; altText = 'public'; displayOrder = 0 }) 'public post'
    $publicId = [long](Get-JsonProperty $public 'id' 'public post id')
    $publicPublishedAt = Get-JsonProperty $public 'publishedAt' 'public publishedAt'
    Assert-True ($null -ne $publicPublishedAt) 'public post publishedAt'
    $publicDetail = Get-PostDetail $publicId $owner.Token 'owner public detail'
    Assert-SanitizedDetail $publicDetail $validPng.ImageUrl 'public detail'
    Assert-Equal (Get-JsonProperty $publicDetail 'visibility' 'public detail visibility') 'PUBLIC' 'public visibility'
    Assert-Equal (Get-JsonProperty (Get-JsonProperty $publicDetail 'category' 'public category') 'id' 'public category id') $defaultCategoryId 'null category resolves DEFAULT'

    $wrongBearerPublic = Get-PostDetail $publicId $null 'invalid bearer public detail' 401 'AUTH_004' 'Bearer not-a-real-token'

    $universeImageBody = New-ContentJson 'universe text' $universeImage.ImageUrl
    $universe = New-Post $owner $defaultCategoryId "M4 QA universe $([Guid]::NewGuid().ToString('N').Substring(0, 8))" $universeImageBody `
        '<p>universe</p>' 'UNIVERSE' $true $null @('universe-m4') `
        @(@{ imageUrl = $universeImage.ImageUrl; altText = 'universe'; displayOrder = 0 }) 'universe post'
    $universeId = [long](Get-JsonProperty $universe 'id' 'universe post id')

    $privateImageBody = New-ContentJson 'private text' $privateImage.ImageUrl
    $private = New-Post $owner $defaultCategoryId "M4 QA private $([Guid]::NewGuid().ToString('N').Substring(0, 8))" $privateImageBody `
        '<p>private</p>' 'PRIVATE' $true $null @('private-m4') `
        @(@{ imageUrl = $privateImage.ImageUrl; altText = 'private'; displayOrder = 0 }) 'private post'
    $privateId = [long](Get-JsonProperty $private 'id' 'private post id')

    $draftJson = New-ContentJson '' $draftImage.ImageUrl
    $draft = New-Post $owner $defaultCategoryId '' $draftJson '<p></p>' 'PRIVATE' $false $null @('draft-m4') `
        @(@{ imageUrl = $draftImage.ImageUrl; altText = 'draft'; displayOrder = 0 }) 'draft post'
    $draftId = [long](Get-JsonProperty $draft 'id' 'draft post id')
    Assert-Null (Get-JsonProperty $draft 'publishedAt' 'draft publishedAt') 'draft publishedAt'

    $transition = New-Post $owner $defaultCategoryId '' @{ type = 'doc'; content = @() } '<p></p>' 'PUBLIC' $false $null @() @() 'transition draft'
    $transitionId = [long](Get-JsonProperty $transition 'id' 'transition id')
    $transitionDraft = Get-PostDetail $transitionId $owner.Token 'transition draft detail'
    Assert-Equal (Get-JsonProperty $transitionDraft 'title' 'transition draft title') '' 'transition draft permits empty title'
    $transitionTitle = 'M4 QA transition published'
    $transitionPublished = Update-PostSnapshot $owner $transitionDraft (New-ContentJson 'transition' '') '<p>transition</p>' $true $null @() 'draft to publish' -Title $transitionTitle
    Assert-Equal (Get-JsonProperty $transitionPublished 'title' 'transition published title') $transitionTitle 'draft to publish saves title'
    $firstTransitionPublishedAt = [string](Get-JsonProperty $transitionPublished 'publishedAt' 'transition publishedAt')
    Assert-True (-not [string]::IsNullOrWhiteSpace($firstTransitionPublishedAt)) 'draft to publish sets publishedAt'
    $rePublished = Update-PostSnapshot $owner $transitionPublished (New-ContentJson 'transition again' '') '<p>transition again</p>' $true $null @() 'republish transition'
    Assert-Equal (Get-JsonProperty $rePublished 'publishedAt' 'republish publishedAt') $firstTransitionPublishedAt 'republish preserves publishedAt'
    $backToDraft = Update-PostSnapshot $owner $rePublished @{ type = 'doc'; content = @() } '<p></p>' $false $null @() 'publish to draft'
    Assert-Null (Get-JsonProperty $backToDraft 'publishedAt' 'publish to draft publishedAt') 'publish to draft clears publishedAt'

    Assert-Equal (Get-PostDetail $publicId $null 'anonymous public detail' 200).id $publicId 'anonymous public access'
    Assert-Equal (Get-PostDetail $universeId $null 'anonymous universe detail' 401 'AUTH_004') $null 'anonymous universe authentication required'
    Assert-Equal (Get-PostDetail $privateId $null 'anonymous private detail' 401 'AUTH_004') $null 'anonymous private authentication required'
    Assert-Equal (Get-PostDetail $draftId $null 'anonymous draft detail' 401 'AUTH_004') $null 'anonymous draft authentication required'
    Assert-Equal (Get-PostDetail $universeId $owner.Token 'owner universe detail' 200).id $universeId 'owner universe access'
    Assert-Equal (Get-PostDetail $privateId $owner.Token 'owner private detail' 200).id $privateId 'owner private access'
    Assert-Equal (Get-PostDetail $draftId $owner.Token 'owner draft detail' 200).id $draftId 'owner draft access'
    Assert-Equal (Get-PostDetail $universeId $forward.Token 'forward universe detail' 200).id $universeId 'forward universe access'
    Assert-Equal (Get-PostDetail $universeId $reverse.Token 'reverse universe detail' 403 'POST_002') $null 'reverse universe denied'
    Assert-Equal (Get-PostDetail $universeId $unrelated.Token 'unrelated universe detail' 403 'POST_002') $null 'unrelated universe denied'
    Assert-Equal (Get-PostDetail $privateId $forward.Token 'forward private detail' 403 'POST_002') $null 'forward private denied'
    Assert-Equal (Get-PostDetail $draftId $forward.Token 'forward draft detail' 403 'POST_002') $null 'forward draft denied'

    $publicImageAnonymous = Invoke-Binary $validPng.ImageUrl
    Assert-BinarySuccess $publicImageAnonymous 'anonymous public image' 'image/png'
    $publicImageForward = Invoke-Binary $validPng.ImageUrl $forward.Token
    Assert-BinarySuccess $publicImageForward 'forward public image' 'image/png'
    $universeImageForward = Invoke-Binary $universeImage.ImageUrl $forward.Token
    Assert-BinarySuccess $universeImageForward 'forward universe image' 'image/gif'
    $universeImageOwner = Invoke-Binary $universeImage.ImageUrl $owner.Token
    Assert-BinarySuccess $universeImageOwner 'owner universe image' 'image/gif'
    $universeImageAnonymous = Invoke-Request -Method 'GET' -Path $universeImage.ImageUrl
    Assert-ApiResponse $universeImageAnonymous 404 'anonymous universe image' 'UPLOAD_003'
    $universeImageReverse = Invoke-Request -Method 'GET' -Path $universeImage.ImageUrl -AccessToken $reverse.Token
    Assert-ApiResponse $universeImageReverse 404 'reverse universe image' 'UPLOAD_003'
    $privateImageOwner = Invoke-Binary $privateImage.ImageUrl $owner.Token
    Assert-BinarySuccess $privateImageOwner 'owner private image' 'image/webp'
    $privateImageAnonymous = Invoke-Request -Method 'GET' -Path $privateImage.ImageUrl
    Assert-ApiResponse $privateImageAnonymous 404 'anonymous private image' 'UPLOAD_003'
    $privateImageForward = Invoke-Request -Method 'GET' -Path $privateImage.ImageUrl -AccessToken $forward.Token
    Assert-ApiResponse $privateImageForward 404 'forward private image' 'UPLOAD_003'
    $draftImageAnonymous = Invoke-Request -Method 'GET' -Path $draftImage.ImageUrl
    Assert-ApiResponse $draftImageAnonymous 404 'anonymous draft image' 'UPLOAD_003'
    $draftImageForward = Invoke-Request -Method 'GET' -Path $draftImage.ImageUrl -AccessToken $forward.Token
    Assert-ApiResponse $draftImageForward 404 'forward draft image' 'UPLOAD_003'
    $draftImageOwner = Invoke-Binary $draftImage.ImageUrl $owner.Token
    Assert-BinarySuccess $draftImageOwner 'owner draft image' 'image/png'

    $publicBlogList = Get-PostList ("/api/v1/blogs/{0}/posts?page=0&size=100&sort=latest" -f $owner.BlogId) $null 'anonymous blog list'
    Assert-ContainsId $publicBlogList $publicId 'anonymous blog list public'
    Assert-ExcludesId $publicBlogList $universeId 'anonymous blog list universe'
    Assert-ExcludesId $publicBlogList $privateId 'anonymous blog list private'
    Assert-ExcludesId $publicBlogList $draftId 'anonymous blog list draft'
    $forwardBlogList = Get-PostList ("/api/v1/blogs/{0}/posts?page=0&size=100&sort=latest" -f $owner.BlogId) $forward.Token 'forward blog list'
    Assert-ContainsId $forwardBlogList $universeId 'forward blog list universe'
    Assert-ExcludesId $forwardBlogList $privateId 'forward blog list private'
    $reverseBlogList = Get-PostList ("/api/v1/blogs/{0}/posts?page=0&size=100&sort=latest" -f $owner.BlogId) $reverse.Token 'reverse blog list'
    Assert-ExcludesId $reverseBlogList $universeId 'reverse blog list universe'
    $ownerBlogList = Get-PostList ("/api/v1/blogs/{0}/posts?page=0&size=100&sort=latest" -f $owner.BlogId) $owner.Token 'owner blog list'
    Assert-ContainsId $ownerBlogList $privateId 'owner blog list private'
    $slugList = Get-PostList ("/api/v1/blogs/slug/{0}/posts?page=0&size=100&sort=latest" -f $owner.BlogSlug) $forward.Token 'forward slug list'
    Assert-ContainsId $slugList $universeId 'forward slug list universe'
    $tagList = Get-PostList '/api/v1/tags/qa-m4/posts?page=0&size=100&sort=latest' $null 'anonymous normalized tag list'
    Assert-ContainsId $tagList $publicId 'normalized tag list public'
    $draftList = Get-PostList '/api/v1/posts/drafts?page=0&size=100' $owner.Token 'owner drafts list'
    Assert-ContainsId $draftList $draftId 'owner drafts list draft'
    $anonymousDraftList = Get-PostList '/api/v1/posts/drafts?page=0&size=100' $null 'anonymous drafts list' 401 'AUTH_004'
    $nonOwnerPublishFalse = Get-PostList ("/api/v1/blogs/{0}/posts?page=0&size=100&publish=false" -f $owner.BlogId) $forward.Token 'non-owner draft filter' 403 'POST_002'

    $viewProbe = New-Post $owner $defaultCategoryId 'M4 QA view probe' (New-ContentJson 'view probe' '') '<p>view probe</p>' `
        'PUBLIC' $true $null @('view-m4') @() 'view probe post'
    $viewProbeId = [long](Get-JsonProperty $viewProbe 'id' 'view probe id')
    $publicDetailBeforeViews = Get-PostDetail $viewProbeId $null 'public view first'
    $publicDetailAfterViews = Get-PostDetail $viewProbeId $null 'public view second'
    $firstViewCount = [long](Get-JsonProperty $publicDetailBeforeViews 'viewCount' 'public first view count')
    $secondViewCount = [long](Get-JsonProperty $publicDetailAfterViews 'viewCount' 'public second view count')
    Assert-True ($firstViewCount -ge 1) 'first anonymous view increments viewCount'
    Assert-Equal $secondViewCount $firstViewCount 'same anonymous IP+UA counts once within 24h'

    $detachedDetail = Update-PostSnapshot $owner $publicDetail (New-ContentJson 'detached from image' '') '<p>detached from image</p>' $true $null @() 'detach body image'
    $detachedImages = Invoke-Request -Method 'PUT' -Path ("/api/v1/posts/{0}/images" -f $publicId) -AccessToken $owner.Token -Body @{ images = @() }
    Assert-ApiResponse $detachedImages 200 'empty image snapshot sync'
    $detachedOwnerRead = Invoke-Binary $validPng.ImageUrl $owner.Token
    Assert-BinarySuccess $detachedOwnerRead 'owner detached image' 'image/png'
    $detachedAnonymousRead = Invoke-Request -Method 'GET' -Path $validPng.ImageUrl
    Assert-ApiResponse $detachedAnonymousRead 404 'anonymous detached image' 'UPLOAD_003'
    $rebindAttempt = New-Post $owner $defaultCategoryId 'M4 QA rebind rejected' (New-ContentJson 'rebind' $validPng.ImageUrl) '<p>rebind</p>' `
        'PUBLIC' $true $null @() @(@{ imageUrl = $validPng.ImageUrl; altText = 'rebind'; displayOrder = 0 }) `
        'already bound image rebind' 400 'UPLOAD_004'

    $wrongPurpose = New-Post $owner $defaultCategoryId 'M4 QA purpose rejected' (New-ContentJson 'purpose') '<p>purpose</p>' `
        'PUBLIC' $true $duplicateOrderImage.ImageUrl @() @() 'wrong upload purpose' 400 'UPLOAD_004'
    $externalImage = New-Post $owner $defaultCategoryId 'M4 QA external rejected' (New-ContentJson 'external' 'https://example.invalid/x.png') '<p>external</p>' `
        'PUBLIC' $true $null @() @(@{ imageUrl = 'https://example.invalid/x.png'; altText = 'external'; displayOrder = 0 }) `
        'external image URL' 400 'UPLOAD_004'

    $nonOwnerImageMutation = Invoke-Request -Method 'PUT' -Path ("/api/v1/posts/{0}/images" -f $universeId) -AccessToken $forward.Token -Body @{ images = @() }
    Assert-ApiResponse $nonOwnerImageMutation 403 'non-owner image mutation' 'POST_003'
    $anonymousImageMutation = Invoke-Request -Method 'PUT' -Path ("/api/v1/posts/{0}/images" -f $universeId) -Body @{ images = @() }
    Assert-ApiResponse $anonymousImageMutation 401 'anonymous image mutation' 'AUTH_004'

    $privateDelete = Invoke-Request -Method 'DELETE' -Path ("/api/v1/posts/{0}" -f $privateId) -AccessToken $owner.Token
    Assert-ApiResponse $privateDelete 200 'owner private delete'
    [void](Get-PostDetail $privateId $owner.Token 'deleted private detail' 404 'POST_001')
    $deletedPrivateImage = Invoke-Request -Method 'GET' -Path $privateImage.ImageUrl -AccessToken $owner.Token
    Assert-ApiResponse $deletedPrivateImage 404 'deleted post image' 'UPLOAD_003'

    Write-Host 'M4 API smoke passed for the executed HTTP gates.'
    Write-Host 'DEFERRED: 24h boundary/cleanup, DB migration/locks/concurrency, restart, FE/browser, OpenAPI, full build, and S3/U0 remain separate acceptance gates.'
} catch {
    Write-Error ("M4 API smoke failed: {0}" -f $_.Exception.Message)
    exit 1
} finally {
    if ($null -ne $HttpClient) {
        $HttpClient.Dispose()
    }
    if ($null -ne $HttpHandler) {
        $HttpHandler.Dispose()
    }
}
