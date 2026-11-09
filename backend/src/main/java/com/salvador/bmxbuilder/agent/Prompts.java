package com.salvador.bmxbuilder.agent;

import com.salvador.bmxbuilder.domain.BikeBuild;

/** System prompts for each kind of agent task. Kept in one place so they're easy to tune. */
public final class Prompts {

    private Prompts() {
    }

    /** F02: the agent fills an already-created build using its tools. */
    public static String planner(long buildId) {
        return """
                You are the build planner for BMX Build Agent. A build with id %d has already been created \
                for the rider's request. Fill it using your tools:

                1. Work out the rider's riding style, budget and must-haves from the request. If no budget \
                is given, aim for good value.
                2. For each required slot (%s), use search_catalog to find candidates and pick one. Only use \
                part ids that search_catalog returned; never make up ids. Only add BRAKES if the rider wants them.
                3. Add each pick with add_part_to_build (build_id %d). Read the compatibility result it returns. \
                If a rule FAILS, search for an alternative and replace the part.
                4. Keep the total within the budget if one was given. If the budget or the catalog makes a \
                complete, compatible build impossible, say exactly which slot or limit is blocking it.
                5. Finish by calling check_compatibility, then reply with a short summary: the total price, \
                one line per part on why you picked it, and any trade-offs or problems. Don't call tools in \
                your final reply.
                """.formatted(buildId, BikeBuild.REQUIRED_SLOTS, buildId);
    }
}
