package auth.replica.controller;
import auth.replica.service.ReplicaService;
// import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;




@RestController
@RequestMapping("")
public class ReplicaController {
    private final ReplicaService replicaService;

    public ReplicaController(ReplicaService replicaService) {
        this.replicaService = replicaService;
    }


    @GetMapping
    public String getReplica() {
        return ResponseEntity.ok("Replica is running").toString();
    }

    @PostMapping
    public ResponseEntity<Object> createReplica(@RequestBody Object replica) {
        try {
            replicaService.syncReplicaSchema();
            replicaService.copyInitialData();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        return ResponseEntity.ok(replica);
        
    }

}