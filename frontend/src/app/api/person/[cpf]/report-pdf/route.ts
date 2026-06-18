import { NextRequest, NextResponse } from "next/server";
import { chromium } from "playwright";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export async function GET(
  request: NextRequest,
  context: { params: Promise<{ cpf: string }> }
) {
  const { cpf } = await context.params;
  const cleanCpf = cpf.replace(/\D/g, "");
  const authHeader = request.headers.get("authorization");

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
      await page.goto(`${request.nextUrl.origin}/print/person/${cleanCpf}`, {
        waitUntil: "networkidle",
      });
      await page.waitForLoadState("networkidle");

      const pdfBuffer = await page.pdf({
        printBackground: true,
        preferCSSPageSize: true,
      });

      return new NextResponse(new Uint8Array(pdfBuffer), {
        headers: {
          "Content-Type": "application/pdf",
          "Content-Disposition": `attachment; filename="relatorio_pf_${cleanCpf}.pdf"`,
          "Cache-Control": "no-store",
        },
      });
    } finally {
      await browser.close();
    }
  } catch (error) {
    console.error("Erro ao gerar PDF detalhado da pessoa fisica:", error);
    return NextResponse.json(
      {
        error:
          "Nao foi possivel gerar o PDF detalhado da pessoa fisica. Verifique se o Chromium do Playwright esta instalado no servidor.",
      },
      { status: 500 }
    );
  }
}
