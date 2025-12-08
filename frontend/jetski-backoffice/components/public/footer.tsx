import Link from 'next/link'
import { Anchor, Mail, Phone, MapPin, Instagram, Facebook, Linkedin } from 'lucide-react'

export function Footer() {
  return (
    <footer className="bg-black border-t border-white/[0.05]">
      {/* Main Footer */}
      <div className="container py-16">
        <div className="grid gap-12 md:grid-cols-2 lg:grid-cols-4">
          {/* Brand */}
          <div className="lg:col-span-1">
            <Link href="/" className="flex items-center gap-3 mb-6">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-white/5 border border-white/10">
                <Anchor className="h-5 w-5 text-gold" />
              </div>
              <div className="flex flex-col">
                <span className="text-lg font-display font-medium text-white tracking-wide">
                  Pega o Jet
                </span>
              </div>
            </Link>
            <p className="text-sm text-white/40 leading-relaxed max-w-xs">
              O marketplace premium de aluguel de jetskis e lanchas no Brasil.
            </p>

            {/* Social */}
            <div className="flex gap-3 mt-6">
              <a
                href="#"
                className="w-9 h-9 rounded-full bg-white/5 flex items-center justify-center text-white/40 hover:text-gold hover:bg-white/10 transition-all duration-300"
              >
                <Instagram className="h-4 w-4" />
              </a>
              <a
                href="#"
                className="w-9 h-9 rounded-full bg-white/5 flex items-center justify-center text-white/40 hover:text-gold hover:bg-white/10 transition-all duration-300"
              >
                <Facebook className="h-4 w-4" />
              </a>
              <a
                href="#"
                className="w-9 h-9 rounded-full bg-white/5 flex items-center justify-center text-white/40 hover:text-gold hover:bg-white/10 transition-all duration-300"
              >
                <Linkedin className="h-4 w-4" />
              </a>
            </div>
          </div>

          {/* Links */}
          <div>
            <h3 className="text-xs text-white/60 tracking-[0.2em] uppercase mb-6">
              Navegação
            </h3>
            <ul className="space-y-3">
              <li>
                <Link href="/" className="text-sm text-white/40 hover:text-gold transition-colors duration-300">
                  Início
                </Link>
              </li>
              <li>
                <Link href="#ofertas" className="text-sm text-white/40 hover:text-gold transition-colors duration-300">
                  Embarcações
                </Link>
              </li>
              <li>
                <Link href="#como-funciona" className="text-sm text-white/40 hover:text-gold transition-colors duration-300">
                  Como Funciona
                </Link>
              </li>
            </ul>
          </div>

          {/* Para Empresas */}
          <div>
            <h3 className="text-xs text-white/60 tracking-[0.2em] uppercase mb-6">
              Para Empresas
            </h3>
            <ul className="space-y-3">
              <li>
                <Link href="/login" className="text-sm text-white/40 hover:text-gold transition-colors duration-300">
                  Acessar Painel
                </Link>
              </li>
              <li>
                <Link href="/signup" className="text-sm text-white/40 hover:text-gold transition-colors duration-300">
                  Cadastrar Empresa
                </Link>
              </li>
              <li>
                <span className="text-sm text-white/20">
                  Planos (em breve)
                </span>
              </li>
            </ul>
          </div>

          {/* Contato */}
          <div>
            <h3 className="text-xs text-white/60 tracking-[0.2em] uppercase mb-6">
              Contato
            </h3>
            <ul className="space-y-4">
              <li>
                <a
                  href="mailto:contato@jetski.com.br"
                  className="flex items-center gap-3 text-sm text-white/40 hover:text-gold transition-colors duration-300"
                >
                  <Mail className="h-4 w-4" />
                  contato@jetski.com.br
                </a>
              </li>
              <li>
                <a
                  href="tel:+5548999999999"
                  className="flex items-center gap-3 text-sm text-white/40 hover:text-gold transition-colors duration-300"
                >
                  <Phone className="h-4 w-4" />
                  (48) 99999-9999
                </a>
              </li>
              <li>
                <div className="flex items-start gap-3 text-sm text-white/40">
                  <MapPin className="h-4 w-4 mt-0.5" />
                  <span>Florianópolis, SC<br />Brasil</span>
                </div>
              </li>
            </ul>
          </div>
        </div>
      </div>

      {/* Bottom Bar */}
      <div className="border-t border-white/[0.05]">
        <div className="container py-6 flex flex-col md:flex-row justify-between items-center gap-4">
          <p className="text-xs text-white/30">
            &copy; {new Date().getFullYear()} Pega o Jet. Todos os direitos reservados.
          </p>
          <div className="flex gap-6 text-xs text-white/30">
            <Link href="#" className="hover:text-gold transition-colors duration-300">
              Termos de Uso
            </Link>
            <Link href="#" className="hover:text-gold transition-colors duration-300">
              Privacidade
            </Link>
            <Link href="#" className="hover:text-gold transition-colors duration-300">
              Cookies
            </Link>
          </div>
        </div>
      </div>
    </footer>
  )
}
