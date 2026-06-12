# Controle do Bar — Android Nativo v1.1.0

Aplicativo Android nativo, offline, para vendas e estoque de bar.

## Fluxos revisados
- Dashboard com vendas do dia, faturamento e itens para comprar.
- Cadastro, edição e arquivamento de produtos.
- Entrada e ajuste de estoque com validação contra saldo negativo.
- Carrinho com validação da soma de quantidades repetidas do mesmo produto.
- Finalização transacional: venda, itens e baixa de estoque são gravados juntos.
- Relatório diário por forma de pagamento e produtos mais vendidos.
- Lista do que precisa comprar conforme estoque mínimo.
- Histórico e detalhes de vendas.
- Backup e restauração completos em JSON, incluindo movimentos de estoque.
- Guia interativo offline.

## Compatibilidade
- Android 7.0 ou superior (minSdk 24).
- Interface responsiva: em celulares, campos e botões passam para disposição vertical.
- Dados salvos no SQLite interno do aparelho.

## Gerar APK
1. Envie todo o conteúdo deste projeto para a raiz do repositório GitHub.
2. Abra Actions > Gerar APK Android.
3. Execute Run workflow.
4. Baixe o artefato Controle-Bar-v1.1.0-APK.

## Atualização
A versão do banco foi elevada para 2 com migração preservando produtos e vendas existentes.
