'use client'

import Link from 'next/link'
import { useState, useEffect } from 'react'
import { Menu, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { Logo } from '@/components/logo'
import { usePortalUrl } from '@/components/public/portal-link'

const navLinks = [
  { href: '/', label: 'Início' },
  { href: '/#ofertas', label: 'Marketplace' },
  { href: '/#como-funciona', label: 'Como Funciona' },
  { href: '/para-empresas', label: 'Para Empresas' },
]

export function Navbar() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [scrolled, setScrolled] = useState(false)
  const portalUrl = usePortalUrl()

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 50)
    }
    window.addEventListener('scroll', handleScroll)
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  return (
    <header
      className={cn(
        'fixed top-0 left-0 right-0 z-50 transition-all duration-500',
        // Mobile: fundo SÓLIDO ao scrollar — backdrop-blur é instável/ausente em
        // navegadores mobile e a transparência deixa o texto vazando por trás.
        scrolled
          ? 'bg-abyss border-b border-white/10 md:bg-abyss/90 md:backdrop-blur-xl'
          : 'bg-transparent'
      )}
    >
      <nav className="container flex h-20 items-center justify-between">
        {/* Logo */}
        <Link href="/" className="flex items-center group">
          <Logo variant="full" theme="dark" size={26} />
        </Link>

        {/* Desktop Navigation */}
        <div className="hidden md:flex items-center gap-8">
          {navLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="text-sm text-white/60 hover:text-white transition-colors duration-300 tracking-wide"
            >
              {link.label}
            </Link>
          ))}
        </div>

        {/* Desktop CTA */}
        <div className="hidden md:flex items-center gap-4">
          <Button
            variant="ghost"
            className="text-white/70 hover:text-white hover:bg-white/10 transition-all duration-300"
            asChild
          >
            <Link href="/login">Portal da Empresa</Link>
          </Button>
          <Button
            className="bg-gold text-[#231A05] hover:bg-gold/90 transition-all duration-300 rounded-none font-medium"
            asChild
          >
            <a href={portalUrl}>Portal do Cliente</a>
          </Button>
        </div>

        {/* Mobile Menu Button */}
        <button
          className="md:hidden p-2 text-white/70 hover:text-white transition-colors"
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          aria-label="Toggle menu"
        >
          {mobileMenuOpen ? (
            <X className="h-6 w-6" />
          ) : (
            <Menu className="h-6 w-6" />
          )}
        </button>
      </nav>

      {/* Mobile Menu */}
      <div
        className={cn(
          // Painel sólido: blur/transparência no mobile deixa o conteúdo vazando por trás.
          'md:hidden absolute top-full left-0 right-0 bg-abyss border-b border-white/10 transition-all duration-300',
          mobileMenuOpen ? 'opacity-100 visible' : 'opacity-0 invisible'
        )}
      >
        <div className="container py-8 space-y-6">
          {navLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className="block text-lg text-white/70 hover:text-white transition-colors"
              onClick={() => setMobileMenuOpen(false)}
            >
              {link.label}
            </Link>
          ))}
          <div className="pt-6 border-t border-white/10 space-y-3">
            <Button className="w-full bg-gold text-[#231A05] hover:bg-gold/90 rounded-none font-medium" asChild>
              <a href={portalUrl} onClick={() => setMobileMenuOpen(false)}>Portal do Cliente</a>
            </Button>
            <Button
              variant="outline"
              className="w-full bg-transparent border-white/30 text-white hover:bg-white/10 hover:text-white rounded-none"
              asChild
            >
              <Link href="/login" onClick={() => setMobileMenuOpen(false)}>Portal da Empresa</Link>
            </Button>
          </div>
        </div>
      </div>
    </header>
  )
}
