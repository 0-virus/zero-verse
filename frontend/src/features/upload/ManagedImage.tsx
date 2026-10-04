import { useEffect, useRef, useState, type CSSProperties } from 'react';
import { useOptionalAuth } from '../../lib/authContext';
import { fetchManagedImage, resolveManagedImage } from './uploadApi';

export interface ManagedImageProps {
  src?: string | null;
  alt: string;
  className?: string;
  style?: CSSProperties;
  width?: number;
  height?: number;
}

function revoke(url: string | null): void {
  if (url && typeof URL.revokeObjectURL === 'function') URL.revokeObjectURL(url);
}

export function ManagedImage({ src, alt, className, style, width, height }: ManagedImageProps) {
  const auth = useOptionalAuth();
  const userId = auth?.user?.id ?? null;
  const [blobState, setBlobState] = useState<{
    url: string;
    src: string;
    userId: number | null;
  } | null>(null);
  const generation = useRef(0);

  useEffect(() => {
    const currentGeneration = ++generation.current;
    let cancelled = false;
    let ownedBlobUrl: string | null = null;
    setBlobState(null);

    const managed = src ? resolveManagedImage(src) : null;
    if (!managed) {
      return () => {
        cancelled = true;
        revoke(ownedBlobUrl);
      };
    }

    void fetchManagedImage(managed.path)
      .then((blob) => {
        if (cancelled || generation.current !== currentGeneration) return;
        const nextUrl = URL.createObjectURL(blob);
        ownedBlobUrl = nextUrl;
        setBlobState({ url: nextUrl, src: src ?? '', userId });
      })
      .catch(() => {
        if (!cancelled && generation.current === currentGeneration) setBlobState(null);
      });

    return () => {
      cancelled = true;
      generation.current += 1;
      revoke(ownedBlobUrl);
      ownedBlobUrl = null;
    };
  }, [src, userId]);

  const managed = src ? resolveManagedImage(src) : null;
  // Effect cleanup runs after React has committed a new render. Keep the identity on the
  // state so an account switch cannot paint the previous user's Blob during that gap.
  const isCurrentBlob =
    !!managed && blobState?.src === src && blobState?.userId === userId;
  const displaySrc = managed && isCurrentBlob && blobState ? blobState.url : managed ? null : src;
  if (!displaySrc) {
    return (
      <span
        role="img"
        aria-label={alt}
        className={className}
        style={{ ...style, width, height }}
      />
    );
  }

  return <img src={displaySrc} alt={alt} className={className} style={style} width={width} height={height} />;
}
