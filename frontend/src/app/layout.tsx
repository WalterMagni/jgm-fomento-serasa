import type { Metadata, Viewport } from "next";
import { Space_Grotesk, Source_Serif_4, Hanken_Grotesk } from "next/font/google";
import localFont from "next/font/local";
import "./globals.css";

const spaceGrotesk = Space_Grotesk({
  variable: "--font-space-grotesk",
  subsets: ["latin"],
});

const sourceSerif = Source_Serif_4({
  variable: "--font-source-serif",
  subsets: ["latin"],
});

// Piloto de repaginação: corpo Hanken Grotesk + títulos Clash Display.
const hankenGrotesk = Hanken_Grotesk({
  variable: "--font-hanken",
  subsets: ["latin"],
});

// Clash Display auto-hospedado (era CDN da Fontshare). Os tablets podem ficar
// numa rede sem internet — fonte externa quebraria os títulos.
const clashDisplay = localFont({
  variable: "--font-clash",
  display: "swap",
  src: [
    { path: "../fonts/ClashDisplay-500.woff2", weight: "500", style: "normal" },
    { path: "../fonts/ClashDisplay-600.woff2", weight: "600", style: "normal" },
    { path: "../fonts/ClashDisplay-700.woff2", weight: "700", style: "normal" },
  ],
});

export const metadata: Metadata = {
  title: "JGM Fomento",
  description: "Sistema Avançado de Análise de Crédito Serasa e CNPJ",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  // Sem maximumScale: o usuário do tablet precisa poder ampliar.
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR" className={`${spaceGrotesk.variable} ${sourceSerif.variable} ${hankenGrotesk.variable} ${clashDisplay.variable}`}>
      {/*
        Sem <link> para CDN de fontes: Google Fonts vêm do next/font (auto-hospedadas
        no build) e o Clash Display é servido de src/fonts. Os antigos links de
        Material Icons foram removidos — o sistema usa Lucide (bundle), não Material.
      */}
      <body className="antialiased font-sans bg-background-light text-grafite dark:bg-background-dark dark:text-areia transition-colors duration-300">
        {children}
      </body>
    </html>
  );
}
