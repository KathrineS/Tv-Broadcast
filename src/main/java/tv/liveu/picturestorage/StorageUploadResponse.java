/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tv.liveu.picturestorage;

/**
 *
 * @author slava
 */
public class StorageUploadResponse {
    private final int id;
    private final String filename;
    
    public StorageUploadResponse(int id, String filename) {
        this.id = id;
        this.filename = filename;
    }
    
    public int getId() {
        return this.id;
    }
    
    public String getFilename() {
        return this.filename;
    }
}
