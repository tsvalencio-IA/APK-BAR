package com.thiaguinho.controlebar;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private DatabaseHelper db;
    private LinearLayout root, content, nav;
    private final JSONArray cart = new JSONArray();
    private final SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    private final SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final SimpleDateFormat br = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("pt", "BR"));

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"), uri -> { if (uri != null) exportTo(uri); });
    private final ActivityResultLauncher<String[]> importLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) importFrom(uri); });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DatabaseHelper(this);
        buildShell();
        showDashboard();
    }

    private void buildShell() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(243,244,246));
        root.addView(header());
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(12),dp(12),dp(12),dp(110));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        nav = new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL); nav.setPadding(dp(6),dp(6),dp(6),dp(6)); nav.setBackgroundColor(Color.WHITE);
        addNav("Início", v -> showDashboard()); addNav("Venda", v -> showSale()); addNav("Estoque", v -> showProducts()); addNav("Relatórios", v -> showReports()); addNav("Mais", v -> showMore());
        root.addView(nav); setContentView(root);
    }

    private View header() {
        LinearLayout h = new LinearLayout(this); h.setOrientation(LinearLayout.HORIZONTAL); h.setGravity(Gravity.CENTER_VERTICAL); h.setPadding(dp(14),dp(16),dp(14),dp(16)); h.setBackgroundColor(Color.rgb(17,24,39));
        LinearLayout text = new LinearLayout(this); text.setOrientation(LinearLayout.VERTICAL);
        TextView title = tv("Controle do Bar",20,true,Color.WHITE); TextView sub = tv("Vendas • Estoque • Relatórios",12,false,Color.LTGRAY);
        text.addView(title); text.addView(sub); h.addView(text,new LinearLayout.LayoutParams(0,-2,1));
        Button help = button("Ajuda", Color.rgb(37,99,235)); help.setOnClickListener(v -> startActivity(new Intent(this,GuideActivity.class))); h.addView(help);
        return h;
    }

    private void addNav(String label, View.OnClickListener l) { Button b=button(label,Color.rgb(31,41,55)); b.setTextSize(11); b.setOnClickListener(l); nav.addView(b,new LinearLayout.LayoutParams(0,dp(52),1)); }
    private void clear(String title) { content.removeAllViews(); LinearLayout top=row(); Button back=button("Voltar",Color.DKGRAY); back.setOnClickListener(v->showDashboard()); top.addView(back); TextView t=tv(title,22,true,Color.rgb(17,24,39)); top.addView(t,new LinearLayout.LayoutParams(0,-2,1)); content.addView(top); }

    private void showDashboard() {
        clear("Visão geral");
        String today=day.format(new Date());
        LinearLayout stats=row(); stats.addView(stat("Vendas hoje",String.valueOf(db.dailyCount(today))),weight()); stats.addView(stat("Total hoje",DatabaseHelper.money(db.dailyTotal(today))),weight()); content.addView(stats);
        Cursor low=db.lowStockProducts(); int n=low.getCount(); low.close(); content.addView(stat("Produtos para comprar",String.valueOf(n)));
        content.addView(section("Ações rápidas"));
        LinearLayout actions=row(); Button sale=button("Nova venda",Color.rgb(22,163,74)); sale.setOnClickListener(v->showSale()); Button prod=button("Novo produto",Color.rgb(37,99,235)); prod.setOnClickListener(v->showProductForm(null)); actions.addView(sale,weight()); actions.addView(prod,weight()); content.addView(actions);
        Button reports=button("Abrir relatórios",Color.rgb(217,119,6)); reports.setOnClickListener(v->showReports()); content.addView(reports,full());
    }

    private void showProducts() { showProducts(""); }
    private void showProducts(String search) {
        clear("Estoque"); EditText q=input("Pesquisar produto, categoria ou código"); q.setText(search); Button find=button("Pesquisar",Color.rgb(37,99,235)); find.setOnClickListener(v->showProducts(q.getText().toString())); LinearLayout sr=row(); sr.addView(q,new LinearLayout.LayoutParams(0,-2,1)); sr.addView(find); content.addView(sr);
        Button add=button("Cadastrar produto",Color.rgb(22,163,74)); add.setOnClickListener(v->showProductForm(null)); content.addView(add,full());
        Cursor c=db.products(search); while(c.moveToNext()) { long id=c.getLong(c.getColumnIndexOrThrow("id")); String name=c.getString(c.getColumnIndexOrThrow("name")); double stock=c.getDouble(c.getColumnIndexOrThrow("stock")); double min=c.getDouble(c.getColumnIndexOrThrow("min_stock")); double price=c.getDouble(c.getColumnIndexOrThrow("price")); String unit=c.getString(c.getColumnIndexOrThrow("unit"));
            LinearLayout card=card(); card.addView(tv(name,17,true,Color.rgb(17,24,39))); card.addView(tv("Estoque: "+fmt(stock)+" "+unit+"  •  Mínimo: "+fmt(min)+"  •  Venda: "+DatabaseHelper.money(price),13,false,stock<=min?Color.RED:Color.DKGRAY));
            LinearLayout a=row(); Button edit=button("Editar",Color.rgb(37,99,235)); edit.setOnClickListener(v->showProductForm(id)); Button in=button("Entrada",Color.rgb(22,163,74)); in.setOnClickListener(v->stockEntry(id,name)); Button del=button("Excluir",Color.rgb(220,38,38)); del.setOnClickListener(v->confirmDeleteProduct(id)); a.addView(edit,weight());a.addView(in,weight());a.addView(del,weight());card.addView(a);content.addView(card);
        } c.close();
    }

    private void showProductForm(Long id) {
        clear(id==null?"Novo produto":"Editar produto");
        EditText name=input("Nome do produto"); EditText category=input("Categoria"); EditText barcode=input("Código de barras"); EditText cost=num("Custo"); EditText price=num("Preço de venda"); EditText stock=num("Estoque atual"); EditText min=num("Estoque mínimo"); EditText unit=input("Unidade: un, lata, garrafa, kg..."); unit.setText("un");
        if(id!=null){ Cursor c=db.getReadableDatabase().rawQuery("SELECT * FROM products WHERE id=?",new String[]{String.valueOf(id)}); if(c.moveToFirst()){ name.setText(c.getString(c.getColumnIndexOrThrow("name"))); category.setText(c.getString(c.getColumnIndexOrThrow("category"))); barcode.setText(c.getString(c.getColumnIndexOrThrow("barcode"))); cost.setText(fmt(c.getDouble(c.getColumnIndexOrThrow("cost")))); price.setText(fmt(c.getDouble(c.getColumnIndexOrThrow("price")))); stock.setText(fmt(c.getDouble(c.getColumnIndexOrThrow("stock")))); min.setText(fmt(c.getDouble(c.getColumnIndexOrThrow("min_stock")))); unit.setText(c.getString(c.getColumnIndexOrThrow("unit"))); } c.close(); }
        for(View v:new View[]{name,category,barcode,cost,price,stock,min,unit}) content.addView(v,full());
        Button save=button("Salvar produto",Color.rgb(17,24,39)); save.setOnClickListener(v->{ if(name.getText().toString().trim().isEmpty()){toast("Informe o nome.");return;} db.saveProduct(id,name.getText().toString().trim(),category.getText().toString().trim(),barcode.getText().toString().trim(),d(cost),d(price),d(stock),d(min),unit.getText().toString().trim(),iso.format(new Date())); toast("Produto salvo."); showProducts(); }); content.addView(save,full());
    }

    private void stockEntry(long id,String name){ EditText qty=num("Quantidade de entrada"); new AlertDialog.Builder(this).setTitle("Entrada: "+name).setView(qty).setNegativeButton("Cancelar",null).setPositiveButton("Salvar",(d,w)->{db.adjustStock(id,d(qty),iso.format(new Date()));showProducts();}).show(); }
    private void confirmDeleteProduct(long id){ new AlertDialog.Builder(this).setTitle("Excluir produto?").setMessage("As vendas antigas continuam preservadas.").setNegativeButton("Cancelar",null).setPositiveButton("Excluir",(d,w)->{db.deleteProduct(id);showProducts();}).show(); }

    private void showSale() {
        clear("Nova venda"); cartClearView();
        Spinner product=new Spinner(this); ArrayList<String> labels=new ArrayList<>(); ArrayList<Long> ids=new ArrayList<>(); ArrayList<Double> prices=new ArrayList<>(); ArrayList<Double> stocks=new ArrayList<>(); Cursor c=db.products(""); while(c.moveToNext()){labels.add(c.getString(c.getColumnIndexOrThrow("name"))+" — estoque "+fmt(c.getDouble(c.getColumnIndexOrThrow("stock")))); ids.add(c.getLong(c.getColumnIndexOrThrow("id"))); prices.add(c.getDouble(c.getColumnIndexOrThrow("price"))); stocks.add(c.getDouble(c.getColumnIndexOrThrow("stock")));} c.close();
        product.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels)); EditText qty=num("Quantidade"); qty.setText("1"); EditText unitPrice=num("Preço unitário");
        product.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){} public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long i){if(pos<prices.size())unitPrice.setText(fmt(prices.get(pos)));}});
        content.addView(product,full()); LinearLayout qr=row(); qr.addView(qty,weight()); qr.addView(unitPrice,weight()); content.addView(qr);
        Button add=button("Adicionar item",Color.rgb(37,99,235)); content.addView(add,full()); LinearLayout cartBox=card(); content.addView(cartBox);
        Spinner payment=new Spinner(this); payment.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Dinheiro","PIX","Cartão","Fiado","Outro"})); EditText notes=input("Observações da venda"); content.addView(payment,full());content.addView(notes,full()); Button finish=button("Finalizar venda",Color.rgb(22,163,74));content.addView(finish,full());
        Runnable render=()->renderCart(cartBox);
        add.setOnClickListener(v->{int pos=product.getSelectedItemPosition(); if(pos<0||pos>=ids.size()){toast("Cadastre produtos primeiro.");return;} double q=d(qty), p=d(unitPrice); if(q<=0||p<0){toast("Quantidade ou valor inválido.");return;} if(q>stocks.get(pos)){toast("Estoque insuficiente. Disponível: "+fmt(stocks.get(pos)));return;} try{JSONObject o=new JSONObject();o.put("productId",ids.get(pos));o.put("productName",labels.get(pos).split(" — ")[0]);o.put("quantity",q);o.put("unitPrice",p);o.put("subtotal",q*p);cart.put(o);render.run();}catch(Exception e){toast(e.getMessage());}});
        finish.setOnClickListener(v->{if(cart.length()==0){toast("Adicione pelo menos um item.");return;}try{long id=db.createSale(cart,payment.getSelectedItem().toString(),notes.getText().toString(),iso.format(new Date()));toast("Venda #"+id+" salva.");cartClearView();showDashboard();}catch(Exception e){new AlertDialog.Builder(this).setTitle("Erro").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}});
        render.run();
    }

    private void renderCart(LinearLayout box){box.removeAllViews();box.addView(section("Itens da venda"));double total=0;for(int i=0;i<cart.length();i++){try{JSONObject o=cart.getJSONObject(i);total+=o.getDouble("subtotal");int idx=i;LinearLayout r=row();TextView t=tv(o.getString("productName")+"\n"+fmt(o.getDouble("quantity"))+" × "+DatabaseHelper.money(o.getDouble("unitPrice"))+" = "+DatabaseHelper.money(o.getDouble("subtotal")),13,true,Color.DKGRAY);r.addView(t,new LinearLayout.LayoutParams(0,-2,1));Button x=button("Remover",Color.rgb(220,38,38));x.setOnClickListener(v->{cart.remove(idx);renderCart(box);});r.addView(x);box.addView(r);}catch(Exception ignored){}}box.addView(tv("Total: "+DatabaseHelper.money(total),20,true,Color.rgb(22,101,52)));}
    private void cartClearView(){while(cart.length()>0)cart.remove(0);}

    private void showReports(){clear("Relatórios");Button daily=button("Vendas diárias",Color.rgb(37,99,235));daily.setOnClickListener(v->showDailyReport(day.format(new Date())));Button buy=button("O que precisa comprar",Color.rgb(217,119,6));buy.setOnClickListener(v->showBuyReport());Button history=button("Histórico de vendas",Color.rgb(17,24,39));history.setOnClickListener(v->showSalesHistory());content.addView(daily,full());content.addView(buy,full());content.addView(history,full());}
    private void showDailyReport(String date){clear("Vendas do dia");Button choose=button("Escolher data: "+date,Color.rgb(37,99,235));choose.setOnClickListener(v->{Calendar cal=Calendar.getInstance();new DatePickerDialog(this,(p,y,m,d)->showDailyReport(String.format(Locale.US,"%04d-%02d-%02d",y,m+1,d)),cal.get(Calendar.YEAR),cal.get(Calendar.MONTH),cal.get(Calendar.DAY_OF_MONTH)).show();});content.addView(choose,full());content.addView(stat("Total do dia",DatabaseHelper.money(db.dailyTotal(date))));content.addView(stat("Número de vendas",String.valueOf(db.dailyCount(date))));renderSales(db.salesForDay(date));}
    private void showBuyReport(){clear("Lista de compras");Cursor c=db.lowStockProducts();if(c.getCount()==0)content.addView(tv("Nenhum produto abaixo do estoque mínimo.",15,true,Color.rgb(22,101,52)));while(c.moveToNext()){LinearLayout card=card();String name=c.getString(c.getColumnIndexOrThrow("name"));double stock=c.getDouble(c.getColumnIndexOrThrow("stock")),min=c.getDouble(c.getColumnIndexOrThrow("min_stock")),suggest=c.getDouble(c.getColumnIndexOrThrow("suggested"));String unit=c.getString(c.getColumnIndexOrThrow("unit"));card.addView(tv(name,17,true,Color.rgb(153,27,27)));card.addView(tv("Atual: "+fmt(stock)+" "+unit+" • Mínimo: "+fmt(min)+" • Comprar: "+fmt(suggest)+" "+unit,13,false,Color.DKGRAY));content.addView(card);}c.close();}
    private void showSalesHistory(){clear("Histórico de vendas");renderSales(db.allSales());}
    private void renderSales(Cursor c){while(c.moveToNext()){long id=c.getLong(c.getColumnIndexOrThrow("id"));String at=c.getString(c.getColumnIndexOrThrow("sold_at"));double total=c.getDouble(c.getColumnIndexOrThrow("total"));String pay=c.getString(c.getColumnIndexOrThrow("payment_method"));int count=c.getInt(c.getColumnIndexOrThrow("item_count"));LinearLayout card=card();card.addView(tv("Venda #"+id+" — "+DatabaseHelper.money(total),17,true,Color.rgb(17,24,39)));card.addView(tv(formatDate(at)+" • "+pay+" • "+count+" item(ns)",13,false,Color.DKGRAY));Button details=button("Ver itens",Color.rgb(37,99,235));details.setOnClickListener(v->showSaleItems(id,total));card.addView(details,full());content.addView(card);}c.close();}
    private void showSaleItems(long saleId,double total){clear("Venda #"+saleId);Cursor c=db.saleItems(saleId);while(c.moveToNext()){content.addView(tv(c.getString(c.getColumnIndexOrThrow("product_name"))+" — "+fmt(c.getDouble(c.getColumnIndexOrThrow("quantity")))+" × "+DatabaseHelper.money(c.getDouble(c.getColumnIndexOrThrow("unit_price")))+" = "+DatabaseHelper.money(c.getDouble(c.getColumnIndexOrThrow("subtotal"))),14,true,Color.DKGRAY));}c.close();content.addView(stat("Total",DatabaseHelper.money(total)));}

    private void showMore(){clear("Mais opções");Button exp=button("Exportar todos os dados",Color.rgb(37,99,235));exp.setOnClickListener(v->exportLauncher.launch("backup_controle_bar_"+day.format(new Date())+".json"));Button imp=button("Importar backup completo",Color.rgb(22,163,74));imp.setOnClickListener(v->importLauncher.launch(new String[]{"application/json","text/plain"}));Button guide=button("Abrir guia interativo",Color.rgb(217,119,6));guide.setOnClickListener(v->startActivity(new Intent(this,GuideActivity.class)));content.addView(exp,full());content.addView(imp,full());content.addView(guide,full());content.addView(tv("A importação substitui todos os dados atuais. Faça um backup antes.",13,true,Color.RED));}

    private void exportTo(Uri uri){try(OutputStream out=getContentResolver().openOutputStream(uri)){JSONObject root=db.exportAll();root.put("exportedAt",iso.format(new Date()));out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));toast("Backup exportado.");}catch(Exception e){toast("Erro: "+e.getMessage());}}
    private void importFrom(Uri uri){new AlertDialog.Builder(this).setTitle("Importar backup?").setMessage("Todos os dados atuais serão substituídos.").setNegativeButton("Cancelar",null).setPositiveButton("Importar",(d,w)->{try(BufferedReader r=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri),StandardCharsets.UTF_8))){StringBuilder s=new StringBuilder();String line;while((line=r.readLine())!=null)s.append(line);db.importAll(new JSONObject(s.toString()));toast("Backup restaurado.");showDashboard();}catch(Exception e){toast("Erro: "+e.getMessage());}}).show();}

    private LinearLayout card(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);v.setPadding(dp(14),dp(14),dp(14),dp(14));v.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);v.setElevation(dp(2));v.setLayoutParams(margin());return v;}
    private View stat(String label,String value){LinearLayout c=card();c.addView(tv(label.toUpperCase(),11,true,Color.GRAY));c.addView(tv(value,20,true,Color.rgb(17,24,39)));return c;}
    private TextView section(String s){TextView t=tv(s,16,true,Color.rgb(17,24,39));t.setPadding(0,dp(10),0,dp(6));return t;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private Button button(String s,int color){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackgroundColor(color);b.setPadding(dp(8),0,dp(8),0);return b;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(15);e.setSingleLine();e.setPadding(dp(12),dp(10),dp(12),dp(10));return e;}
    private EditText num(String hint){EditText e=input(hint);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);return e;}
    private TextView tv(String s,int size,boolean bold,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setPadding(dp(4),dp(4),dp(4),dp(4));return t;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(dp(4),dp(4),dp(4),dp(4));return p;}
    private LinearLayout.LayoutParams full(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));return p;}
    private LinearLayout.LayoutParams margin(){LinearLayout.LayoutParams p=full();p.setMargins(0,dp(6),0,dp(6));return p;}
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density);}
    private double d(EditText e){try{return Double.parseDouble(e.getText().toString().trim().replace(',','.'));}catch(Exception x){return 0;}}
    private String fmt(double v){return String.format(new Locale("pt","BR"),"%.2f",v);}
    private String formatDate(String isoText){try{return br.format(iso.parse(isoText));}catch(Exception e){return isoText;}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
