package in.skillsignal.match;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MatchWeightsProperties {

    @Value("${app.match.weights.required-skills:60}")
    private int requiredSkills = 60;

    @Value("${app.match.weights.nice-to-have-skills:10}")
    private int niceToHaveSkills = 10;

    @Value("${app.match.weights.experience:20}")
    private int experience = 20;

    @Value("${app.match.weights.location:10}")
    private int location = 10;

    public int getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(int requiredSkills) { this.requiredSkills = requiredSkills; }

    public int getNiceToHaveSkills() { return niceToHaveSkills; }
    public void setNiceToHaveSkills(int niceToHaveSkills) { this.niceToHaveSkills = niceToHaveSkills; }

    public int getExperience() { return experience; }
    public void setExperience(int experience) { this.experience = experience; }

    public int getLocation() { return location; }
    public void setLocation(int location) { this.location = location; }
}
