"use client";

import { useEffect, useState } from "react";
import QRCode from "qrcode";

/**
 * QR Code renderizado no cliente a partir do PIX "copia e cola" (o BR Code EMV
 * é o próprio conteúdo do QR) — mesma técnica do backoffice: nada vai a
 * serviços externos.
 */
export function PixQr({ payload, size = 176 }: { payload?: string; size?: number }) {
  const [dataUrl, setDataUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!payload) {
      setDataUrl(null);
      return;
    }
    let vivo = true;
    QRCode.toDataURL(payload, { width: size, margin: 1 })
      .then((url) => vivo && setDataUrl(url))
      .catch(() => vivo && setDataUrl(null));
    return () => {
      vivo = false;
    };
  }, [payload, size]);

  if (!dataUrl) return null;
  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={dataUrl}
      alt="QR Code PIX"
      width={size}
      height={size}
      className="rounded-2xl border border-slate-200 bg-white p-1.5"
    />
  );
}
