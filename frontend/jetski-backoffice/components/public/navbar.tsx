'use client'

import Link from 'next/link'
import { useState, useEffect } from 'react'
import { Menu, X, Anchor } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

const navLinks = [
  { href: '/', label: 'Início' },
  { href: '#ofertas', label: 'Embarcações' },
  { href: '#como-funciona', label: 'Como Funciona' },
]

export function Navbar() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [scrolled, setScrolled] = useState(false)

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
        scrolled
          ? 'bg-black/90 backdrop-blur-xl border-b border-white/10'
          : 'bg-transparent'
      )}
    >
      <nav className="container flex h-20 items-center justify-between">
        {/* Logo */}
        <Link href="/" className="flex items-center gap-3 group">
          <div className={cn(
            "flex h-10 w-10 items-center justify-center rounded-full transition-all duration-500",
            scrolled ? "bg-white/10" : "bg-white/5 border border-white/10"
          )}>
            <Anchor className="h-5 w-5 text-gold" />
          </div>
          <div className="flex flex-col">
            <span className="text-lg font-display font-medium text-white tracking-wide">
              Pega o Jet
            </span>
          </div>
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
            <Link href="/login">Acesso Empresa</Link>
          </Button>
          <Button
            className={cn(
              "transition-all duration-500 rounded-none",
              scrolled
                ? "bg-white text-black hover:bg-white/90"
                : "bg-white/10 text-white border border-white/20 hover:bg-white/20"
            )}
            asChild
          >
            <Link href="/signup">Cadastrar Empresa</Link>
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
          'md:hidden absolute top-full left-0 right-0 bg-black/95 backdrop-blur-xl border-b border-white/10 transition-all duration-300',
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
            <Button
              variant="outline"
              className="w-full border-white/20 text-white hover:bg-white/10 rounded-none"
              asChild
            >
              <Link href="/login">Acesso Empresa</Link>
            </Button>
            <Button className="w-full bg-white text-black hover:bg-white/90 rounded-none" asChild>
              <Link href="/signup">Cadastrar Empresa</Link>
            </Button>
          </div>
        </div>
      </div>
    </header>
  )
}
