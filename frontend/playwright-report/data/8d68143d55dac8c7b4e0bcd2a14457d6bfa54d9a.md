# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - generic [ref=e3]:
    - generic [ref=e4]:
      - generic [ref=e5]: C
      - heading "CredAnalyze" [level=1] [ref=e6]
      - paragraph [ref=e7]: Acesse o sistema de Análise de Crédito
    - generic [ref=e8]:
      - generic [ref=e9]: Failed to fetch
      - generic [ref=e10]:
        - generic [ref=e11]: E-mail
        - textbox "seu.nome@empresa.com.br" [ref=e12]: admin@portal.com
      - generic [ref=e13]:
        - generic [ref=e14]: Senha
        - textbox "••••••••" [ref=e15]: Admin@12345
      - generic [ref=e16]:
        - generic [ref=e17]:
          - checkbox "Lembrar-me" [ref=e18]
          - generic [ref=e19]: Lembrar-me
        - link "Esqueceu a senha?" [ref=e20] [cursor=pointer]:
          - /url: "#"
      - button "Entrar" [ref=e21]
    - generic [ref=e22]:
      - text: Não possui uma conta?
      - link "Criar conta" [ref=e23] [cursor=pointer]:
        - /url: /register
  - button "Open Next.js Dev Tools" [ref=e29] [cursor=pointer]:
    - img [ref=e30]
  - alert [ref=e33]
```