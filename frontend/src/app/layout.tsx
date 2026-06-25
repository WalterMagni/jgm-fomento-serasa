import type { Metadata } from "next";
import { Space_Grotesk, Source_Serif_4, Hanken_Grotesk } from "next/font/google";
import "./globals.css";

const spaceGrotesk = Space_Grotesk({
  variable: "--font-space-grotesk",
  subsets: ["latin"],
});

const sourceSerif = Source_Serif_4({
  variable: "--font-source-serif",
  subsets: ["latin"],
});

// Piloto de repaginação: corpo Hanken Grotesk + títulos Clash Display (via Fontshare).
const hankenGrotesk = Hanken_Grotesk({
  variable: "--font-hanken",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "JGM Fomento",
  description: "Sistema Avançado de Análise de Crédito Serasa e CNPJ",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR" className={`${spaceGrotesk.variable} ${sourceSerif.variable} ${hankenGrotesk.variable}`}>
      {/*
        Google Material Icons used by the design system
      */}
      <head>
        <link href="https://fonts.googleapis.com/icon?family=Material+Icons+Outlined" rel="stylesheet" />
        {/* eslint-disable-next-line @next/next/no-page-custom-font */}
        <link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap" rel="stylesheet" />
        {/* Clash Display (Fontshare) — títulos do piloto de repaginação */}
        <link href="https://api.fontshare.com/v2/css?f[]=clash-display@500,600,700&display=swap" rel="stylesheet" />
      </head>
      <body className="antialiased font-sans bg-background-light text-grafite dark:bg-background-dark dark:text-areia transition-colors duration-300">
        {children}
      </body>
    </html>
  );
}
