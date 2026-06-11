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
    private static final int DB_VERSION = 1;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE products (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "category TEXT DEFAULT ''," +
                "barcode TEXT DEFAULT ''," +
                "cost REAL DEFAULT 0," +
                "price REAL DEFAULT 0," +
                "stock REAL DEFAULT 0," +
                "min_stock REAL DEFAULT 0," +
                "unit TEXT DEFAULT 'un'," +
                "created_at TEXT NOT NULL," +
                "updated_at TEXT NOT NULL)");

        db.execSQL("CREATE TABLE sales (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "total REAL DEFAULT 0," +
                "payment_method TEXT DEFAULT 'Dinheiro'," +
                "notes TEXT DEFAULT ''," +
                "sold_at TEXT NOT NULL)");

        db.execSQL("CREATE TABLE sale_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "sale_id INTEGER NOT NULL," +
                "product_id INTEGER," +
                "product_name TEXT NOT NULL," +
                "quantity REAL DEFAULT 0," +
                "unit_price REAL DEFAULT 0," +
                "subtotal REAL DEFAULT 0," +
                "FOREIGN KEY(sale_id) REFERENCES sales(id) ON DELETE CASCADE)");

        db.execSQL("CREATE INDEX idx_products_name ON products(name)");
        db.execSQL("CREATE INDEX idx_sales_date ON sales(sold_at)");
        db.execSQL("CREATE INDEX idx_sale_items_sale ON sale_items(sale_id)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public long saveProduct(Long id, String name, String category, String barcode, double cost,
                            double price, double stock, double minStock, String unit, String now) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("name", name);
        v.put("category", category);
        v.put("barcode", barcode);
        v.put("cost", cost);
        v.put("price", price);
        v.put("stock", stock);
        v.put("min_stock", minStock);
        v.put("unit", unit);
        v.put("updated_at", now);
        if (id == null) {
            v.put("created_at", now);
            return db.insertOrThrow("products", null, v);
        }
        db.update("products", v, "id=?", new String[]{String.valueOf(id)});
        return id;
    }

    public void deleteProduct(long id) {
        getWritableDatabase().delete("products", "id=?", new String[]{String.valueOf(id)});
    }

    public void adjustStock(long id, double delta, String now) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE products SET stock = stock + ?, updated_at=? WHERE id=?",
                new Object[]{delta, now, id});
    }

    public Cursor products(String search) {
        String q = search == null ? "" : search.trim();
        if (q.isEmpty()) {
            return getReadableDatabase().rawQuery("SELECT * FROM products ORDER BY name COLLATE NOCASE", null);
        }
        return getReadableDatabase().rawQuery(
                "SELECT * FROM products WHERE name LIKE ? OR category LIKE ? OR barcode LIKE ? ORDER BY name COLLATE NOCASE",
                new String[]{"%" + q + "%", "%" + q + "%", "%" + q + "%"});
    }

    public Cursor lowStockProducts() {
        return getReadableDatabase().rawQuery(
                "SELECT *, CASE WHEN min_stock-stock > 0 THEN min_stock-stock ELSE 0 END AS suggested " +
                        "FROM products WHERE stock <= min_stock ORDER BY (min_stock-stock) DESC, name COLLATE NOCASE", null);
    }

    public long createSale(JSONArray items, String paymentMethod, String notes, String soldAt) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            double total = 0;
            for (int i = 0; i < items.length(); i++) total += items.getJSONObject(i).getDouble("subtotal");

            ContentValues sale = new ContentValues();
            sale.put("total", total);
            sale.put("payment_method", paymentMethod);
            sale.put("notes", notes);
            sale.put("sold_at", soldAt);
            long saleId = db.insertOrThrow("sales", null, sale);

            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                long productId = item.optLong("productId", 0);
                double qty = item.getDouble("quantity");

                ContentValues si = new ContentValues();
                si.put("sale_id", saleId);
                if (productId > 0) si.put("product_id", productId);
                si.put("product_name", item.getString("productName"));
                si.put("quantity", qty);
                si.put("unit_price", item.getDouble("unitPrice"));
                si.put("subtotal", item.getDouble("subtotal"));
                db.insertOrThrow("sale_items", null, si);

                if (productId > 0) {
                    db.execSQL("UPDATE products SET stock = stock - ?, updated_at=? WHERE id=?",
                            new Object[]{qty, soldAt, productId});
                }
            }
            db.setTransactionSuccessful();
            return saleId;
        } finally {
            db.endTransaction();
        }
    }

    public Cursor salesForDay(String yyyyMmDd) {
        return getReadableDatabase().rawQuery(
                "SELECT s.*, COUNT(si.id) AS item_count FROM sales s " +
                        "LEFT JOIN sale_items si ON si.sale_id=s.id " +
                        "WHERE substr(s.sold_at,1,10)=? GROUP BY s.id ORDER BY s.sold_at DESC",
                new String[]{yyyyMmDd});
    }

    public Cursor allSales() {
        return getReadableDatabase().rawQuery(
                "SELECT s.*, COUNT(si.id) AS item_count FROM sales s " +
                        "LEFT JOIN sale_items si ON si.sale_id=s.id GROUP BY s.id ORDER BY s.sold_at DESC", null);
    }

    public Cursor saleItems(long saleId) {
        return getReadableDatabase().rawQuery("SELECT * FROM sale_items WHERE sale_id=? ORDER BY id",
                new String[]{String.valueOf(saleId)});
    }

    public double dailyTotal(String yyyyMmDd) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(total),0) v FROM sales WHERE substr(sold_at,1,10)=?", new String[]{yyyyMmDd});
        try { return c.moveToFirst() ? c.getDouble(0) : 0; } finally { c.close(); }
    }

    public int dailyCount(String yyyyMmDd) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM sales WHERE substr(sold_at,1,10)=?", new String[]{yyyyMmDd});
        try { return c.moveToFirst() ? c.getInt(0) : 0; } finally { c.close(); }
    }

    public JSONObject exportAll() throws Exception {
        JSONObject root = new JSONObject();
        root.put("app", "Controle de Vendas e Estoque do Bar");
        root.put("format", "thiaguinho-bar-backup-v1");
        root.put("products", cursorToJson(getReadableDatabase().rawQuery("SELECT * FROM products ORDER BY id", null)));
        root.put("sales", cursorToJson(getReadableDatabase().rawQuery("SELECT * FROM sales ORDER BY id", null)));
        root.put("saleItems", cursorToJson(getReadableDatabase().rawQuery("SELECT * FROM sale_items ORDER BY id", null)));
        return root;
    }

    public void importAll(JSONObject root) throws Exception {
        JSONArray products = root.optJSONArray("products");
        JSONArray sales = root.optJSONArray("sales");
        JSONArray saleItems = root.optJSONArray("saleItems");
        if (products == null || sales == null || saleItems == null) throw new Exception("Backup incompatível.");

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("sale_items", null, null);
            db.delete("sales", null, null);
            db.delete("products", null, null);
            insertJsonRows(db, "products", products);
            insertJsonRows(db, "sales", sales);
            insertJsonRows(db, "sale_items", saleItems);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private JSONArray cursorToJson(Cursor c) throws Exception {
        JSONArray arr = new JSONArray();
        try {
            String[] cols = c.getColumnNames();
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                for (int i = 0; i < cols.length; i++) {
                    switch (c.getType(i)) {
                        case Cursor.FIELD_TYPE_INTEGER: o.put(cols[i], c.getLong(i)); break;
                        case Cursor.FIELD_TYPE_FLOAT: o.put(cols[i], c.getDouble(i)); break;
                        case Cursor.FIELD_TYPE_STRING: o.put(cols[i], c.getString(i)); break;
                        case Cursor.FIELD_TYPE_NULL: o.put(cols[i], JSONObject.NULL); break;
                        default: o.put(cols[i], c.getString(i));
                    }
                }
                arr.put(o);
            }
        } finally { c.close(); }
        return arr;
    }

    private void insertJsonRows(SQLiteDatabase db, String table, JSONArray rows) throws Exception {
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.getJSONObject(i);
            ContentValues v = new ContentValues();
            JSONArray names = o.names();
            if (names == null) continue;
            for (int n = 0; n < names.length(); n++) {
                String key = names.getString(n);
                Object value = o.opt(key);
                if (value == null || value == JSONObject.NULL) v.putNull(key);
                else if (value instanceof Integer || value instanceof Long) v.put(key, ((Number)value).longValue());
                else if (value instanceof Number) v.put(key, ((Number)value).doubleValue());
                else v.put(key, String.valueOf(value));
            }
            db.insertOrThrow(table, null, v);
        }
    }

    public static String money(double value) {
        return String.format(new Locale("pt", "BR"), "R$ %.2f", value).replace('.', ',');
    }
}
