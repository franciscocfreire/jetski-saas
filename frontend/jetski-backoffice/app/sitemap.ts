import type { MetadataRoute } from 'next'

export default function sitemap(): MetadataRoute.Sitemap {
  const base = 'https://meujet.com.br'
  return [
    { url: base, changeFrequency: 'weekly', priority: 1 },
    { url: `${base}/para-empresas`, changeFrequency: 'monthly', priority: 0.9 },
    { url: `${base}/signup`, changeFrequency: 'monthly', priority: 0.6 },
    { url: `${base}/login`, changeFrequency: 'yearly', priority: 0.3 },
  ]
}
