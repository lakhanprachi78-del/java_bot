package com.ugro.skaleup.rag;
import com.fasterxml.jackson.core.type.TypeReference; import com.fasterxml.jackson.databind.ObjectMapper; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service; import java.nio.file.*; import java.util.*; import java.util.stream.*;
@Service public class VectorStore {
 public record Chunk(String source,int page,String text,float[] vector){}
 private final ObjectMapper mapper; private final EmbeddingService embeddings; private final Path file; private volatile List<Chunk> chunks=List.of();
 public VectorStore(ObjectMapper m,EmbeddingService e,@Value("${RAG_INDEX_FILE:./vector-index.json}")String f){mapper=m;embeddings=e;file=Path.of(f);load();}
 public synchronized int rebuild(List<ChunkText> input)throws Exception{List<Chunk> out=new ArrayList<>();int batch=32;for(int i=0;i<input.size();i+=batch){List<ChunkText>b=input.subList(i,Math.min(input.size(),i+batch));List<float[]>vs=embeddings.embed(b.stream().map(ChunkText::text).toList());for(int j=0;j<b.size();j++)out.add(new Chunk(b.get(j).source(),b.get(j).page(),b.get(j).text(),vs.get(j)));}Files.createDirectories(file.toAbsolutePath().getParent());mapper.writeValue(file.toFile(),out);chunks=out;return out.size();}
 public List<ScoredChunk> search(String q,int k)throws Exception{if(chunks.isEmpty())load();if(chunks.isEmpty())throw new IllegalStateException("Knowledge index is empty. Retrain the model first.");float[]v=embeddings.embedOne(q);return chunks.stream().map(c->new ScoredChunk(c,cos(v,c.vector()))).sorted((a,b)->Float.compare(b.score(),a.score())).limit(k).toList();}
 public record ChunkText(String source,int page,String text){} public record ScoredChunk(Chunk chunk,float score){}
 private float cos(float[]a,float[]b){int n=Math.min(a.length,b.length);double s=0;for(int i=0;i<n;i++)s+=a[i]*b[i];return(float)s;}
 private synchronized void load(){try{if(Files.exists(file))chunks=mapper.readValue(file.toFile(),new TypeReference<List<Chunk>>(){});}catch(Exception e){chunks=List.of();}}
 public void clear(){chunks=List.of();}
}
