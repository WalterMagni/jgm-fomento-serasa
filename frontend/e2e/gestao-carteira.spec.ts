import { expect, test, type Page } from "@playwright/test";

/**
 * Fumaça da Gestão de Carteira: KPI como filtro, filtros na URL e a consulta rápida pedindo
 * confirmação. Lista e métricas são interceptadas com dados fixos, e qualquer consulta a
 * provedor é respondida no navegador — o teste nunca gasta consulta Serasa.
 *
 * Variáveis (iguais às da esteira):
 *   E2E_EMAIL / E2E_SENHA  usuário do teste (padrão: o admin do DatabaseSeeder)
 */
const EMAIL = process.env.E2E_EMAIL ?? "admin@jgm.com.br";
const SENHA = process.env.E2E_SENHA ?? "admin123";

const SEM_ANALISE = {
  client: { id: "c1", documentNumber: "11222333000181", name: "TECELAGEM PRIMAVERA LTDA", clientCode: "693", origin: null, createdAt: "2026-10-01T10:00:00" },
  companyDetail: null,
  creditAnalysis: null,
};
const ANALISADA = {
  client: { id: "c2", documentNumber: "44555666000172", name: "CONFECCOES AURORA LTDA", clientCode: null, origin: null, createdAt: "2026-09-01T10:00:00" },
  companyDetail: null,
  creditAnalysis: {
    id: 1, clientId: "c2", cnpj: "44555666000172", companyName: "CONFECCOES AURORA LTDA", visaoCedente: "SIM",
    negativeSummary: { pefin: { summary: { count: 2, balance: 1500 } } }, inquiryHistory: {},
    consultaEm: "2026-09-02T10:00:00", status: "CONCLUIDO",
  },
};

function pagina(conteudo: unknown[]) {
  return { content: conteudo, totalElements: conteudo.length, totalPages: 1, number: 0, size: 50 };
}

async function entrar(page: Page) {
  await page.goto("/login");
  await page.fill('input[type="email"]', EMAIL);
  await page.fill('input[type="password"]', SENHA);
  await page.keyboard.press("Enter");
  await page.waitForURL(url => !url.pathname.startsWith("/login"));
}

test.describe("Gestão de Carteira", () => {
  let consultas = 0;

  test.beforeEach(async ({ page }) => {
    consultas = 0;
    await page.route("**/company/profiles/metrics", rota =>
      rota.fulfill({ json: { totalClients: 2, analyzedClients: 1, cedenteSimCount: 1, pendingAnalysisCount: 1, cnpjaEnrichedCount: 0, avgScore: 0, highRiskCount: 0, totalDebt: 0 } }),
    );
    await page.route(/\/company\/profiles\?/, rota => {
      const filtro = new URL(rota.request().url()).searchParams.get("analysisStatus");
      const lista = filtro === "PENDING" ? [SEM_ANALISE] : filtro === "ANALYZED" ? [ANALISADA] : [SEM_ANALISE, ANALISADA];
      return rota.fulfill({ json: pagina(lista) });
    });
    await page.route("**/company/enrich/**", rota => {
      consultas++;
      return rota.fulfill({ status: 503, json: { message: "Serasa fora do ar (simulado)." } });
    });
    await entrar(page);
  });

  test("KPI filtra e o filtro fica na URL", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("link", { name: "Tecelagem Primavera Ltda" })).toBeVisible();
    await expect(page.getByRole("link", { name: "Confeccoes Aurora Ltda" })).toBeVisible();

    await page.getByRole("button", { name: /Aguardando análise/ }).click();
    await expect(page.getByRole("link", { name: "Confeccoes Aurora Ltda" })).toBeHidden();
    await expect(page).toHaveURL(/analise=pendente/);

    await page.reload();
    await expect(page.getByRole("button", { name: /Aguardando análise/ })).toHaveAttribute("aria-pressed", "true");
  });

  test("consulta rápida só em quem não tem análise e pede confirmação", async ({ page }) => {
    await page.goto("/");
    const botoes = page.getByRole("button", { name: "Consultar Serasa" });
    await expect(botoes).toHaveCount(1);

    await botoes.first().click();
    expect(consultas, "primeiro clique só pede confirmação").toBe(0);

    await page.getByRole("button", { name: /Confirmar · R\$ 6,33/ }).click();
    await expect(page.getByText("Serasa fora do ar (simulado).")).toBeVisible();
    expect(consultas).toBe(1);
  });
});
