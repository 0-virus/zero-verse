/**
 * The production bundle is served by the same origin as the API. Development and
 * Vitest keep the historical localhost default so local workflows do not need an
 * environment file.
 */
export function resolveApiBaseUrl(
  explicit: string | undefined,
  production: boolean,
  developmentDefault: string,
): string {
  if (explicit !== undefined) {
    const normalized = explicit.trim();
    return normalized.includes('://') ? normalized.replace(/\/+$/, '') : normalized;
  }
  return production ? '' : developmentDefault;
}

function isLoopbackHost(hostname: string): boolean {
  const normalized = hostname.toLowerCase().replace(/^\[|\]$/g, '').replace(/\.+$/, '');
  if (normalized === 'localhost' || normalized.endsWith('.localhost') || normalized === '::1') {
    return true;
  }
  const octets = normalized.split('.');
  return octets.length === 4 && octets[0] === '127' && octets.slice(1).every((octet) => {
    const value = Number(octet);
    return Number.isInteger(value) && value >= 0 && value <= 255;
  });
}

/**
 * Returns a build-time validation message for a production API base URL.
 * An empty value is the supported same-origin configuration. Explicit production
 * values must be HTTPS and must not point to a local machine.
 */
export function productionApiBaseError(explicit: string | undefined): string | null {
  if (explicit === undefined || explicit.trim() === '') return null;

  let url: URL;
  try {
    url = new URL(explicit);
  } catch {
    return 'VITE_API_BASE_URL must be empty or an absolute HTTPS URL in production.';
  }

  if (url.protocol !== 'https:') {
    return 'VITE_API_BASE_URL must use HTTPS in production; leave it empty for same-origin API requests.';
  }
  if (isLoopbackHost(url.hostname)) {
    return 'VITE_API_BASE_URL must not point to localhost or a loopback address in production.';
  }
  if (url.username || url.password || url.search || url.hash || url.pathname !== '/') {
    return 'VITE_API_BASE_URL must be an HTTPS origin without credentials, a path, query, or hash.';
  }
  return null;
}

export function assertProductionApiBaseUrl(explicit: string | undefined): void {
  const error = productionApiBaseError(explicit);
  if (error) throw new Error(error);
}

export function resolveApiOrigin(baseUrl: string, locationOrigin: string): URL {
  return new URL(baseUrl || locationOrigin);
}
