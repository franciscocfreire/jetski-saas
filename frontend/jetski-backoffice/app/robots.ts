import type { MetadataRoute } from 'next'

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: '*',
      allow: '/',
      disallow: ['/dashboard/', '/api/', '/magic-activate', '/logout'],
    },
    sitemap: 'https://meujet.com.br/sitemap.xml',
  }
}
