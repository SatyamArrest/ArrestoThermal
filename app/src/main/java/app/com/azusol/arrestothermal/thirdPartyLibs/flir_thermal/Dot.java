package app.com.azusol.arrestothermal.thirdPartyLibs.flir_thermal;

public class Dot {
    public float x, bitmapX;
    public float y, bitmapY;
    public float radius=30;
    public String dotName = "";

    public Dot(float x, float y, float radius) {
        this.x = x;
        this.y = y;
    }

    public Dot(float x, float y, float bitmapX, float bitmapY) {
        this.x = x;
        this.y = y;
        this.bitmapX = bitmapX;
        this.bitmapY = bitmapY;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getBitmapX() {
        return bitmapX;
    }

    public float getBitmapY() {
        return bitmapY;
    }

    public float getRadius() {
        return radius;
    }

    public String getDotName() {
        return dotName;
    }

    public void setDotName(String dotName) {
        this.dotName = dotName;
    }

    public boolean isInside(float x, float y) {
        return (getX() - x) * (getX() - x) + (getY() - y) * (getY() - y) <= radius * radius;
    }
}