package com.ugro.skaleup.rag;
import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service; import java.util.*; import java.util.stream.Collectors;
@Service public class RagService {private final VectorStore store;private final PdfIngestionService ingest;private final LlmService llm;private final LanguageDetector detector;private final QueryCacheService cache;private final int k;private final double threshold;public RagService(VectorStore s,PdfIngestionService i,LlmService l,LanguageDetector d,QueryCacheService c,@Value("${RAG_MAX_RESULTS:8}")int kk,@Value("${RAG_SIMILARITY_THRESHOLD:0.35}")double t){store=s;ingest=i;llm=l;detector=d;cache=c;k=kk;threshold=t;}
 public ApiModels.RagChatResponse chat(String q,String session,boolean handoff)throws Exception{
  Optional<ApiModels.RagChatResponse> cached=cache.lookup(q);
  if(cached.isPresent())return cached.get();
  List<VectorStore.ScoredChunk> hits=store.search(q,k);
  if(hits.isEmpty()||hits.get(0).score()<threshold)return new ApiModels.RagChatResponse("The current training documents do not cover this topic.",List.of(),null);
  String lang=detector.detect(q);
  String context=hits.stream().map(h->"["+h.chunk().source()+" p."+h.chunk().page()+"] "+h.chunk().text()).collect(Collectors.joining("\n\n"));
  String sys="You are the SkaleUP knowledge-base assistant. Answer only from the supplied training context. Respond in the user's language (detected: "+lang+"). If unsupported, say the training documents do not cover it. "
   +"Formatting rules (the chat UI renders plain text only, no Markdown): "
   +"Do not use '#' headings, do not use '*' or '-' bullet lists, do not use italics. "
   +"Write in short plain paragraphs separated by a blank line. "
   +"You may use **word** around a term to bold it. "
   +"For structured facts (an ID, amount, status, date, etc.), put each on its own line as 'Label: value' — the UI turns consecutive Label: value lines into a clean table automatically.";
  String answer=llm.answer(sys,"Question: "+q+"\n\nTraining context:\n"+context);
  List<ApiModels.ChatSource> sources=hits.stream().map(h->new ApiModels.ChatSource(h.chunk().source(),h.chunk().page(),h.chunk().text().substring(0,Math.min(280,h.chunk().text().length())))).toList();
  return cache.put(q,answer,sources);
 }
 public int retrain()throws Exception{cache.clear();return store.rebuild(ingest.ingest());}
}