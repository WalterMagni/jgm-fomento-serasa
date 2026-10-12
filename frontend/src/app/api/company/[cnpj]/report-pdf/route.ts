import { NextRequest, NextResponse } from "next/server";
import { chromium } from "playwright";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

function buildPdfFileName(name: string | null | undefined, fallback: string) {
  const baseName = (name || fallback).replace(/[\\/:*?"<>|]/g, "").trim().replace(/\s+/g, " ");
  return `${baseName || fallback}.pdf`;
}

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ cnpj: string }> }
) {
  const { cnpj } = await context.params;
  const cleanCnpj = cnpj.replace(/\D/g, "");
  const authHeader = request.headers.get("authorization");
  // Consulta escolhida no histórico; sem ela, a página de impressão usa a mais recente.
  const analysisId = request.nextUrl.searchParams.get("analysisId");
  const consulta = analysisId && /^\d+$/.test(analysisId) ? `?analysisId=${analysisId}` : "";

  try {
    const browser = await chromium.launch({
      headless: true,
      args: ["--no-sandbox", "--disable-setuid-sandbox"],
    });

    try {
      const page = await browser.newPage();
      if (authHeader) {
        await page.setExtraHTTPHeaders({ Authorization: authHeader });
      }

      await page.emulateMedia({ media: "print" });
      await page.goto(`${request.nextUrl.origin}/print/company/${cleanCnpj}${consulta}`, {
        waitUntil: "networkidle",
      });
      await page.waitForLoadState("networkidle");
      const companyName = await page.title().catch(() => "");

      const pdfBuffer = await page.pdf({
        printBackground: true,
        preferCSSPageSize: true,
      });

      return new NextResponse(new Uint8Array(pdfBuffer), {
        headers: {
          "Content-Type": "application/pdf",
          "Content-Disposition": `attachment; filename="${buildPdfFileName(companyName, cleanCnpj)}"`,
          "Cache-Control": "no-store",
        },
      });
    } finally {
      await browser.close();
    }
  } catch (error) {
    console.error("Erro ao gerar PDF detalhado da empresa:", error);
    return NextResponse.json(
      {
        error:
          "Nao foi possivel gerar o PDF detalhado. Se estiver no servidor, verifique se o Chromium do Playwright esta instalado.",
      },
      { status: 500 }
    );
  }
}
