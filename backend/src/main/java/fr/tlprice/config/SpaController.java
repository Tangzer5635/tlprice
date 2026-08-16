package fr.tlprice.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Renvoie l index.html du front pour toutes les routes non-API sans point
 * (SPA React Router). Les mappings /api/** plus specifiques ont priorite.
 */
@Controller
public class SpaController {

    @RequestMapping(value = {"/", "/{chemin:[^\\.]*}", "/{chemin:[^\\.]*}/{sous:[^\\.]*}"})
    public String index() {
        return "forward:/index.html";
    }
}
