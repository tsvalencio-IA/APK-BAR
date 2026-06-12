package com.thiaguinho.controlebar;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "controle_bar.db";
    private static final int DB_VERSION = 2;

    public DatabaseHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,category TEXT DEFAULT '',barcode TEXT DEFAULT '',cost REAL DEFAULT 0,price REAL DEFAULT 0,stock REAL DEFAULT 0,min_stock REAL DEFAULT 0,unit TEXT DEFAULT 'un',active INTEGER DEFAULT 1,created_at TEXT NOT NULL,updated_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT,total REAL DEFAULT 0,payment_method TEXT DEFAULT 'Dinheiro',notes TEXT DEFAULT '',sold_at TEXT NOT NULL)");
        db.execSQL("CREATE TABLE sale_items (id INTEGER PRIMARY KEY AUTOINCREMENT,sale_id INTEGER NOT NULL,product_id INTEGER,product_name TEXT NOT NULL,quantity REAL DEFAULT 0,unit_price REAL DEFAULT 0,subtotal REAL DEFAULT 0,FOREIGN KEY(sale_id) REFERENCES sales(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE stock_movements (id INTEGER PRIMARY KEY AUTOINCREMENT,product_id INTEGER NOT NULL,type TEXT NOT NULL,quantity REAL NOT NULL,reason TEXT DEFAULT '',created_at TEXT NOT NULL,FOREIGN KEY(product_id) REFERENCES products(id))");
        db.execSQL("CREATE INDEX idx_products_name ON products(name)");
        db.execSQL("CREATE INDEX idx_sales_date ON sales(sold_at)");
        db.execSQL("CREATE INDEX idx_sale_items_sale ON sale_items(sale_id)");
        db.execSQL("CREATE INDEX idx_stock_movements_product ON stock_movements(product_id)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE products ADD COLUMN active INTEGER DEFAULT 1"); } catch (Exception ignored) {}
            db.execSQL("CREATE TABLE IF NOT EXISTS stock_movements (id INTEGER PRIMARY KEY AUTOINCREMENT,product_id INTEGER NOT NULL,type TEXT NOT NULL,quantity REAL NOT NULL,reason TEXT DEFAULT '',created_at TEXT NOT NULL,FOREIGN KEY(product_id) REFERENCES products(id))");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_stock_movements_product ON stock_movements(product_id)");
        }
    }

    public long saveProduct(Long id, String name, String category, String barcode, double cost, double price, double stock, double minStock, String unit, String now) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("name", name); v.put("category", category); v.put("barcode", barcode);
            v.put("cost", cost); v.put("price", price); v.put("stock", stock);
            v.put("min_stock", minStock); v.put("unit", unit); v.put("active", 1); v.put("updated_at", now);
            long result;
            if (id == null) { v.put("created_at", now); result = database.insertOrThrow("products", null, v); }
            else { database.update("products", v, "id=?", new String[]{String.valueOf(id)}); result = id; }
            database.setTransactionSuccessful();
            return result;
        } finally { database.endTransaction(); }
    }

    public Cursor product(long id) { return getReadableDatabase().rawQuery("SELECT * FROM products WHERE id=?", new String[]{String.valueOf(id)}); }
    public int productCount() { Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM products",null); try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();} }
    public void archiveProduct(long id) { ContentValues v=new ContentValues(); v.put("active",0); getWritableDatabase().update("products",v,"id=?",new String[]{String.valueOf(id)}); }

    public void adjustStock(long id, double delta, String type, String reason, String now) throws Exception {
        if (delta == 0) throw new Exception("Informe uma quantidade diferente de zero.");
        SQLiteDatabase database=getWritableDatabase(); database.beginTransaction();
        try {
            Cursor c=database.rawQuery("SELECT stock FROM products WHERE id=? AND active=1",new String[]{String.valueOf(id)});
            if(!c.moveToFirst()){c.close();throw new Exception("Produto não encontrado.");}
            double current=c.getDouble(0); c.close();
            double next=current+delta;
            if(next<0) throw new Exception("O estoque não pode ficar negativo.");
            database.execSQL("UPDATE products SET stock=?,updated_at=? WHERE id=?",new Object[]{next,now,id});
            ContentValues m=new ContentValues(); m.put("product_id",id);m.put("type",type);m.put("quantity",delta);m.put("reason",reason);m.put("created_at",now);
            database.insertOrThrow("stock_movements",null,m);
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

    public Cursor products(String search) {
        String q=search==null?"":search.trim();
        if(q.isEmpty()) return getReadableDatabase().rawQuery("SELECT * FROM products WHERE active=1 ORDER BY name COLLATE NOCASE",null);
        return getReadableDatabase().rawQuery("SELECT * FROM products WHERE active=1 AND (name LIKE ? OR category LIKE ? OR barcode LIKE ?) ORDER BY name COLLATE NOCASE",new String[]{"%"+q+"%","%"+q+"%","%"+q+"%"});
    }

    public Cursor lowStockProducts() { return getReadableDatabase().rawQuery("SELECT *,CASE WHEN min_stock-stock>0 THEN min_stock-stock ELSE 0 END AS suggested FROM products WHERE active=1 AND stock<=min_stock ORDER BY (min_stock-stock) DESC,name COLLATE NOCASE",null); }
    public Cursor stockMovements(long productId) { return getReadableDatabase().rawQuery("SELECT * FROM stock_movements WHERE product_id=? ORDER BY created_at DESC LIMIT 100",new String[]{String.valueOf(productId)}); }

    public long createSale(JSONArray items,String paymentMethod,String notes,String soldAt) throws Exception {
        if(items==null||items.length()==0) throw new Exception("Adicione ao menos um item.");
        SQLiteDatabase database=getWritableDatabase(); database.beginTransaction();
        try {
            double total=0;
            for(int i=0;i<items.length();i++){
                JSONObject item=items.getJSONObject(i); long productId=item.optLong("productId",0); double qty=item.getDouble("quantity"); double subtotal=item.getDouble("subtotal");
                if(qty<=0||subtotal<0) throw new Exception("Item inválido no carrinho.");
                if(productId>0){ Cursor c=database.rawQuery("SELECT stock,active FROM products WHERE id=?",new String[]{String.valueOf(productId)}); if(!c.moveToFirst()){c.close();throw new Exception("Um produto do carrinho não existe mais.");} double stock=c.getDouble(0);int active=c.getInt(1);c.close(); if(active!=1)throw new Exception("Um produto do carrinho está inativo."); if(qty>stock+0.000001)throw new Exception("Estoque insuficiente para "+item.getString("productName")+". Disponível: "+formatNumber(stock)); }
                total+=subtotal;
            }
            ContentValues sale=new ContentValues();sale.put("total",round2(total));sale.put("payment_method",paymentMethod);sale.put("notes",notes);sale.put("sold_at",soldAt);
            long saleId=database.insertOrThrow("sales",null,sale);
            for(int i=0;i<items.length();i++){
                JSONObject item=items.getJSONObject(i);long productId=item.optLong("productId",0);double qty=item.getDouble("quantity");
                ContentValues si=new ContentValues();si.put("sale_id",saleId);if(productId>0)si.put("product_id",productId);si.put("product_name",item.getString("productName"));si.put("quantity",qty);si.put("unit_price",item.getDouble("unitPrice"));si.put("subtotal",round2(item.getDouble("subtotal")));database.insertOrThrow("sale_items",null,si);
                if(productId>0){database.execSQL("UPDATE products SET stock=stock-?,updated_at=? WHERE id=?",new Object[]{qty,soldAt,productId});ContentValues m=new ContentValues();m.put("product_id",productId);m.put("type","VENDA");m.put("quantity",-qty);m.put("reason","Venda #"+saleId);m.put("created_at",soldAt);database.insertOrThrow("stock_movements",null,m);}
            }
            database.setTransactionSuccessful(); return saleId;
        } finally { database.endTransaction(); }
    }

    public Cursor salesForDay(String date){return getReadableDatabase().rawQuery("SELECT s.*,COUNT(si.id) item_count FROM sales s LEFT JOIN sale_items si ON si.sale_id=s.id WHERE substr(s.sold_at,1,10)=? GROUP BY s.id ORDER BY s.sold_at DESC",new String[]{date});}
    public Cursor allSales(){return getReadableDatabase().rawQuery("SELECT s.*,COUNT(si.id) item_count FROM sales s LEFT JOIN sale_items si ON si.sale_id=s.id GROUP BY s.id ORDER BY s.sold_at DESC",null);}
    public Cursor saleItems(long saleId){return getReadableDatabase().rawQuery("SELECT * FROM sale_items WHERE sale_id=? ORDER BY id",new String[]{String.valueOf(saleId)});}
    public double dailyTotal(String date){Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(total),0) FROM sales WHERE substr(sold_at,1,10)=?",new String[]{date});try{return c.moveToFirst()?c.getDouble(0):0;}finally{c.close();}}
    public int dailyCount(String date){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sales WHERE substr(sold_at,1,10)=?",new String[]{date});try{return c.moveToFirst()?c.getInt(0):0;}finally{c.close();}}
    public Cursor paymentSummary(String date){return getReadableDatabase().rawQuery("SELECT payment_method,COUNT(*) sale_count,COALESCE(SUM(total),0) total FROM sales WHERE substr(sold_at,1,10)=? GROUP BY payment_method ORDER BY total DESC",new String[]{date});}
    public Cursor topProducts(String date){return getReadableDatabase().rawQuery("SELECT si.product_name,SUM(si.quantity) quantity,SUM(si.subtotal) total FROM sale_items si JOIN sales s ON s.id=si.sale_id WHERE substr(s.sold_at,1,10)=? GROUP BY si.product_name ORDER BY quantity DESC LIMIT 20",new String[]{date});}

    public JSONObject exportAll() throws Exception {JSONObject root=new JSONObject();root.put("app","Controle de Vendas e Estoque do Bar");root.put("format","thiaguinho-bar-backup-v2");root.put("products",cursorToJson(getReadableDatabase().rawQuery("SELECT * FROM products ORDER BY id",null)));root.put("sales",cursorToJson(getReadableDatabase().rawQuery("SELECT * FROM sales ORDER BY id",null)));root.put("saleItems",cursorToJson(getReadableDatabase().rawQuery("SELECT * FROM sale_items ORDER BY id",null)));root.put("stockMovements",cursorToJson(getReadableDatabase().rawQuery("SELECT * FROM stock_movements ORDER BY id",null)));return root;}

    public void importAll(JSONObject root) throws Exception {
        JSONArray products=root.optJSONArray("products"),sales=root.optJSONArray("sales"),saleItems=root.optJSONArray("saleItems"),movements=root.optJSONArray("stockMovements");
        if(products==null||sales==null||saleItems==null)throw new Exception("Backup incompatível."); if(movements==null)movements=new JSONArray();
        SQLiteDatabase database=getWritableDatabase();database.beginTransaction();
        try{database.delete("stock_movements",null,null);database.delete("sale_items",null,null);database.delete("sales",null,null);database.delete("products",null,null);insertJsonRows(database,"products",products);insertJsonRows(database,"sales",sales);insertJsonRows(database,"sale_items",saleItems);insertJsonRows(database,"stock_movements",movements);database.setTransactionSuccessful();}finally{database.endTransaction();}
    }

    private JSONArray cursorToJson(Cursor c) throws Exception {JSONArray arr=new JSONArray();try{String[] cols=c.getColumnNames();while(c.moveToNext()){JSONObject o=new JSONObject();for(int i=0;i<cols.length;i++){switch(c.getType(i)){case Cursor.FIELD_TYPE_INTEGER:o.put(cols[i],c.getLong(i));break;case Cursor.FIELD_TYPE_FLOAT:o.put(cols[i],c.getDouble(i));break;case Cursor.FIELD_TYPE_STRING:o.put(cols[i],c.getString(i));break;case Cursor.FIELD_TYPE_NULL:o.put(cols[i],JSONObject.NULL);break;default:o.put(cols[i],c.getString(i));}}arr.put(o);}}finally{c.close();}return arr;}
    private void insertJsonRows(SQLiteDatabase db,String table,JSONArray rows)throws Exception{for(int i=0;i<rows.length();i++){JSONObject o=rows.getJSONObject(i);ContentValues v=new ContentValues();JSONArray names=o.names();if(names==null)continue;for(int n=0;n<names.length();n++){String key=names.getString(n);Object value=o.opt(key);if(value==null||value==JSONObject.NULL)v.putNull(key);else if(value instanceof Integer||value instanceof Long)v.put(key,((Number)value).longValue());else if(value instanceof Number)v.put(key,((Number)value).doubleValue());else v.put(key,String.valueOf(value));}db.insertOrThrow(table,null,v);}}
    private static double round2(double v){return Math.round(v*100.0)/100.0;}
    public static String money(double value){return String.format(new Locale("pt","BR"),"R$ %.2f",value).replace('.',',');}
    public static String formatNumber(double value){return String.format(new Locale("pt","BR"),"%.2f",value);}
}
