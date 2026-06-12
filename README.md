# Controle do Bar — Android Nativo v1.2.0

Evolução direta da base funcional v1.1.3. O aplicativo continua usando o mesmo `applicationId` e o mesmo banco SQLite, portanto a atualização preserva produtos, estoque, vendas e relatórios já existentes.

## Recursos existentes preservados
- Dashboard com vendas e faturamento do dia.
- Cadastro, edição e arquivamento de produtos.
- Entrada e ajuste de estoque.
- Carrinho e baixa automática de estoque.
- Relatórios diários, histórico e lista do que comprar.
- Exportação e importação completa em JSON.
- Interface Android nativa e offline.

## Novidades v1.2.0
- DEMO de 3 dias iniciada no primeiro produto cadastrado.
- Se a atualização encontrar produtos já cadastrados, a DEMO começa no primeiro uso desta versão.
- Bloqueio após o prazo sem apagar nenhum dado.
- Tela de desbloqueio com senha administrativa.
- Ativação PRO gravada no aparelho e independente do banco SQLite.
- Guia HTML com voz nativa Android sincronizada: a etapa só avança quando a narração termina.
- Ícone profissional do Controle do Bar para a tela do celular.

## Gerar o APK no GitHub
1. Envie o conteúdo interno deste ZIP para a raiz do repositório.
2. Abra **Actions > Gerar APK Android**.
3. Execute **Run workflow**.
4. Baixe o artefato `Controle-Bar-v1.2.0-DEMO-PRO-APK`.

## Atualização sem perder dados
Instale o APK novo por cima do anterior. Não altere o `applicationId` e não desinstale o aplicativo antes de atualizar, pois a desinstalação remove o banco local do Android.


## Assinatura estável e preservação de dados
A partir da v1.2.0 o APK usa uma chave estável incluída no projeto. Isso permite instalar versões futuras por cima desta sem apagar o SQLite.

A v1.1.3 anterior foi assinada por uma chave temporária do GitHub Actions. Antes da primeira instalação da v1.2.0:
1. Abra a v1.1.3 e exporte o backup JSON.
2. Instale a v1.2.0. Se o Android exigir desinstalar a anterior, desinstale somente depois de exportar.
3. Importe o JSON na v1.2.0.
4. Nas próximas atualizações, não será mais necessário desinstalar.
