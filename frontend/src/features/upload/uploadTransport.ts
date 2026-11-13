export type UploadTransport = 'local' | 's3';

/**
 * Direct S3 upload is the production default. Local multipart remains the
 * default for development and tests so a developer does not need AWS
 * credentials merely to run the application.
 */
export function resolveUploadTransport(
  explicit: string | undefined,
  production: boolean,
): UploadTransport {
  const normalized = explicit?.trim().toLowerCase();
  if (normalized === undefined || normalized === '') return production ? 's3' : 'local';
  if (normalized !== 'local' && normalized !== 's3') {
    throw new Error('VITE_UPLOAD_TRANSPORT must be either local or s3.');
  }
  if (production && normalized !== 's3') {
    throw new Error('VITE_UPLOAD_TRANSPORT must be s3 in production.');
  }
  return normalized;
}

export function productionUploadTransportError(explicit: string | undefined): string | null {
  const normalized = explicit?.trim().toLowerCase();
  if (normalized === undefined || normalized === '' || normalized === 's3') return null;
  if (normalized === 'local') return 'VITE_UPLOAD_TRANSPORT must be s3 in production.';
  return 'VITE_UPLOAD_TRANSPORT must be either local or s3.';
}

export function assertProductionUploadTransport(explicit: string | undefined): void {
  const error = productionUploadTransportError(explicit);
  if (error) throw new Error(error);
}
