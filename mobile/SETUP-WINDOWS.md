# Setup do Ambiente Mobile - Windows

Este guia ajuda você a configurar o ambiente de desenvolvimento mobile no Windows para o projeto Jetski.

## 📋 Pré-requisitos

- **Sistema Operacional**: Windows 10/11 (64-bit)
- **RAM**: Mínimo 8GB (recomendado 16GB)
- **Espaço em disco**: ~15GB livres
- **Java**: JDK 17 ou superior (verificar com `java -version`)

---

## 🎯 O que vamos instalar

1. **Android Studio** - IDE oficial para desenvolvimento Android
2. **Android SDK** - Ferramentas e bibliotecas Android
3. **Plugin Kotlin Multiplatform** - Suporte a projetos KMM
4. **Android Emulator** (opcional) - Para testar sem dispositivo físico

---

## 📥 Passo 1: Instalar Android Studio

### 1.1 Download
1. Acesse: https://developer.android.com/studio
2. Clique em **Download Android Studio**
3. Aceite os termos e baixe o instalador (`.exe`, ~1GB)

### 1.2 Instalação
1. Execute o instalador baixado
2. Aceite as configurações padrão:
   - ✅ Android Studio
   - ✅ Android SDK
   - ✅ Android Virtual Device (emulador)
3. Escolha local de instalação (padrão: `C:\Program Files\Android\Android Studio`)
4. Aguarde instalação (~5-10 minutos)

### 1.3 Primeiro Launch
1. Abra Android Studio
2. **Import Settings**: Escolha "Do not import settings" (primeira vez)
3. **Welcome Wizard**:
   - Escolha tema (Light/Dark)
   - Tipo de instalação: **Standard**
   - Componentes verificados:
     - ✅ Android SDK
     - ✅ Android SDK Platform
     - ✅ Android Virtual Device
   - Aceite licenças (clicar em "Accept" para todas)
4. Aguarde download dos componentes (~2-3GB, pode demorar)

---

## 🔧 Passo 2: Configurar Android SDK

### 2.1 Abrir SDK Manager
1. Android Studio → **More Actions** → **SDK Manager**

   Ou: **File** → **Settings** → **Appearance & Behavior** → **System Settings** → **Android SDK**

### 2.2 SDK Platforms (Aba "SDK Platforms")
Marque as seguintes versões:
- ✅ **Android 14.0 (API Level 34)** - Versão target do app
- ✅ **Android 8.0 (API Level 26)** - Versão mínima suportada
- ☑️ Show Package Details:
  - ✅ Android SDK Platform 34
  - ✅ Sources for Android 34

### 2.3 SDK Tools (Aba "SDK Tools")
Verifique que estão instalados (marque se não estiverem):
- ✅ **Android SDK Build-Tools** (última versão)
- ✅ **Android Emulator**
- ✅ **Android SDK Platform-Tools**
- ✅ **Intel x86 Emulator Accelerator (HAXM installer)** - para emulador rápido no Windows

**Nota**: "Android SDK Tools" foi deprecado e não aparece mais nas versões novas do Android Studio.

Clique em **Apply** → **OK** e aguarde download.

### 2.4 Verificar Instalação
No terminal (PowerShell ou CMD):

```powershell
# Verificar Java
java -version
# Deve mostrar Java 17 ou superior

# Verificar Android SDK (adicionar ao PATH se necessário)
# Localização padrão: C:\Users\<seu-usuario>\AppData\Local\Android\Sdk
```

**Adicionar Android SDK ao PATH (opcional mas recomendado)**:
1. Pressione `Win + X` → **System** → **Advanced system settings**
2. **Environment Variables** → Variável **Path** do usuário → **Edit**
3. Adicionar:
   ```
   C:\Users\<seu-usuario>\AppData\Local\Android\Sdk\platform-tools
   C:\Users\<seu-usuario>\AppData\Local\Android\Sdk\tools
   ```
4. Reiniciar terminal e testar: `adb version`

---

## 🔌 Passo 3: Instalar Plugin Kotlin Multiplatform

### 3.1 Abrir Plugins
Android Studio → **File** → **Settings** → **Plugins**

### 3.2 Buscar e Instalar
1. Na aba **Marketplace**, busque: `Kotlin Multiplatform`
2. Encontre: **"Kotlin Multiplatform Mobile"** (by JetBrains)
3. Clique em **Install**
4. Aguarde download
5. Clique em **Restart IDE** quando solicitado

### 3.3 Verificar Instalação
Após reiniciar:
- **File** → **New** → Deve aparecer opção **"Kotlin Multiplatform App"** ✅

---

## 📱 Passo 4: Configurar Emulador (Opcional)

### 4.1 Abrir AVD Manager
Android Studio → **More Actions** → **Virtual Device Manager**

Ou: **Tools** → **Device Manager**

### 4.2 Criar Virtual Device
1. Clique em **Create Virtual Device**
2. **Categoria**: Phone
3. **Device**:
   - **Recomendado**: **Pixel 6** (1080x2400, 420 dpi) - Bom equilíbrio
   - Alternativas: Pixel 8, Pixel 9a, Medium Phone
   - ⚠️ Evite: Pixel Fold, Pro XL (muito pesados)
4. Clique **Next**
5. **System Image**:
   - Selecione **API Level 34** (Android 14.0 "UpsideDownCake")
   - Na coluna "API Level", procure linha com **34** e **Play** icon (✅)
   - Se não estiver baixado, clique no ícone de download
   - Aguarde download (~1-2GB)
6. Clique **Next**
7. **AVD Name**: `Pixel_6_API_34` (ou outro nome descritivo)
8. **Startup orientation**: Portrait
9. **Graphics**: Automatic (ou Hardware se tiver GPU boa)
10. Clique **Finish**

### 4.3 Testar Emulador
1. Na lista de devices, clique no ícone ▶️ (Play) ao lado do emulador criado
2. Aguarde inicialização (primeira vez pode demorar 2-3 minutos)
3. Deve abrir janela com Android funcionando ✅
4. Pode fechar o emulador por enquanto

---

## 🔍 Passo 5: Verificação Final

### 5.1 Checklist de Instalação

Execute no terminal (PowerShell) e verifique as saídas:

```powershell
# Java instalado?
java -version
# Esperado: openjdk version "17.x.x" ou superior ✅

# Android SDK instalado?
adb version
# Esperado: Android Debug Bridge version x.x.x ✅

# Gradle (vem com Android Studio)
# Verificar ao abrir projeto
```

### 5.2 Teste Final: Criar Projeto Teste

1. Android Studio → **New Project**
2. Escolha **"Empty Activity"**
3. **Name**: `TesteSetup`
4. **Language**: Kotlin
5. **Minimum SDK**: API 26 (Android 8.0)
6. Clique **Finish**
7. Aguarde Gradle sync (primeira vez pode demorar 5-10 min)
8. Se Gradle sync completar ✅ → **Ambiente OK!**

---

## 🚀 Próximos Passos

Agora que o ambiente está configurado, você pode:

1. ✅ Prosseguir para criação do projeto Jetski Mobile
2. 📖 Ler `mobile/README.md` para entender a arquitetura
3. 💻 Começar a implementar (seguir `mobile/ARCHITECTURE.md`)

---

## ⚠️ Troubleshooting

### Problema: "SDK location not found"
**Solução**:
1. File → Project Structure → SDK Location
2. Apontar para: `C:\Users\<seu-usuario>\AppData\Local\Android\Sdk`

### Problema: Gradle sync muito lento
**Solução**:
1. File → Settings → Build, Execution, Deployment → Gradle
2. Marcar: "Offline work" (após baixar deps uma vez)
3. Aumentar heap: Help → Edit Custom VM Options → adicionar `-Xmx4096m`

### Problema: Emulador não inicia
**Solução**:
1. Verificar HAXM instalado: SDK Manager → SDK Tools → Intel x86 Emulator Accelerator
2. Verificar virtualização habilitada na BIOS
3. Alternativa: Usar dispositivo físico via USB (habilitar "Developer Options" + "USB Debugging")

### Problema: "adb" não reconhecido
**Solução**: Adicionar SDK ao PATH (ver Passo 2.4)

---

## 📚 Recursos Úteis

- **Android Developer Docs**: https://developer.android.com/docs
- **Kotlin Multiplatform**: https://kotlinlang.org/docs/multiplatform.html
- **Jetpack Compose Tutorial**: https://developer.android.com/jetpack/compose/tutorial
- **Ktor Client Docs**: https://ktor.io/docs/client.html

---

## 🎓 Próxima Etapa: Entender KMM

Antes de começar a codar, leia:
- `mobile/KMM-INTRO.md` - O que é Kotlin Multiplatform Mobile
- `mobile/ARCHITECTURE.md` - Arquitetura do projeto Jetski
- `mobile/jetski-mobile/README.md` - Documentação do projeto mobile

---

**Setup completo! 🎉 Agora você está pronto para desenvolver mobile!**
